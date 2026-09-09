/* ============================================================
   notifications.js — Carelyo Staff Portal
   Mapped to actual NOTIFICATION table schema:
     notificationid (PK), userid (FK→USER), childid (FK→CHILD),
     title, message, type, is_read, created_at, clinicid (FK→CLINIC),
     appid, childvaccineid, medscheduleid, reminderid
   NOTE: No "channel" or "delivery_status" columns exist in the schema.
   ============================================================ */

// ─── Supabase Edge Function URL for FCM push ──────────────────────────────
// Replace <PROJECT_REF> with your Supabase project ref (e.g. hrwppmgrlitutjbqzekt)
const FCM_EDGE_FUNCTION_URL = `${supabaseUrl}/functions/v1/send_fcm_push`;

// ─── DOM ready ────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    loadNotifications();
    loadParentsIntoDropdown();
    wireModal();
});

// ─── Load & render notification table ─────────────────────────────────────
async function loadNotifications() {
    const tableBody = document.getElementById('notifications-table-body');
    if (!tableBody) return;

    try {
        // Join USER to get recipient name via NOTIFICATION.userid → USER.userid
        const { data, error } = await window.supabaseClient
            .from('NOTIFICATION')
            .select(`
                notificationid,
                userid,
                title,
                message,
                type,
                is_read,
                created_at,
                clinicid,
                USER (full_name)
            `)
            .order('created_at', { ascending: false });

        if (error) {
            console.error('Error fetching notifications:', error);
            tableBody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:red;">Failed to load notifications</td></tr>`;
            return;
        }

        const notifications = data || [];

        // ── Stat cards ───────────────────────────────────────────────────
        // Total sent = all rows in NOTIFICATION
        // Read       = is_read IS TRUE
        // Unread     = is_read IS FALSE or NULL
        // (No delivery_status column in schema — "Unread" replaces "Failed")
        const totalCnt  = notifications.length;
        const readCnt   = notifications.filter(n => n.is_read === true).length;
        const unreadCnt = notifications.filter(n => !n.is_read).length;

        document.getElementById('stat-sent').textContent   = totalCnt;
        document.getElementById('stat-read').textContent   = readCnt;
        document.getElementById('stat-failed').textContent = unreadCnt;

        if (notifications.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="5" style="text-align:center;">No notifications found</td></tr>`;
            return;
        }

        tableBody.innerHTML = '';
        notifications.forEach(n => {
            // Recipient name from joined USER table
            const recipient = n.USER ? n.USER.full_name : '—';

            // type: capitalise first letter, display as plain gray text
            const rawType   = (n.type || 'general').toLowerCase();
            const typeLabel = rawType.charAt(0).toUpperCase() + rawType.slice(1);

            // Sent At: "2026-09-05 10:00" (ISO date + HH:MM time)
            const sentAt = n.created_at ? formatDateTime(n.created_at) : '—';

            // Status badges matching reference:
            //   is_read = true  → "Read"  (gray outline)
            //   is_read = false → "Sent"  (teal/green filled)
            const statusBadge = n.is_read
                ? `<span style="display:inline-block;padding:3px 12px;border-radius:9999px;font-size:12px;font-weight:500;border:1px solid #e2e8f0;color:#64748b;background:#f8fafc;">Read</span>`
                : `<span style="display:inline-block;padding:3px 12px;border-radius:9999px;font-size:12px;font-weight:500;border:1px solid #bbf7d0;color:#16a34a;background:#f0fdf4;">Sent</span>`;

            tableBody.insertAdjacentHTML('beforeend', `
                <tr>
                    <td style="color:#64748b;font-size:13px;">${typeLabel}</td>
                    <td style="font-weight:600;color:#111827;">${recipient}</td>
                    <td style="color:#64748b;font-size:13px;max-width:280px;">${n.message || '—'}</td>
                    <td style="white-space:nowrap;font-size:12px;color:#94a3b8;">${sentAt}</td>
                    <td>${statusBadge}</td>
                </tr>
            `);
        });

    } catch (err) {
        console.error('Unexpected error loading notifications:', err);
        document.getElementById('notifications-table-body').innerHTML =
            `<tr><td colspan="5" style="text-align:center;color:red;">Error loading data</td></tr>`;
    }
}

// ─── Populate parent dropdown from USER table ──────────────────────────────
// Schema: USER.userid (PK), USER.full_name text, USER.role text
async function loadParentsIntoDropdown() {
    const select = document.getElementById('notif-recipient');
    if (!select) return;

    try {
        const { data, error } = await window.supabaseClient
            .from('USER')
            .select('userid, full_name')
            .eq('role', 'parent')
            .order('full_name', { ascending: true });

        if (error || !data || data.length === 0) {
            select.innerHTML = `<option value="">No parents found</option>`;
            return;
        }

        select.innerHTML =
            `<option value="">— Select a parent —</option>` +
            data.map(u => `<option value="${u.userid}">${u.full_name}</option>`).join('');

    } catch (err) {
        console.error('Error loading parents:', err);
        select.innerHTML = `<option value="">Error loading parents</option>`;
    }
}

// ─── Modal wiring ──────────────────────────────────────────────────────────
function wireModal() {
    const modal     = document.getElementById('send-modal');
    const btnOpen   = document.getElementById('btn-open-send');
    const btnClose  = document.getElementById('btn-close-modal');
    const btnCancel = document.getElementById('btn-cancel-modal');
    const btnSend   = document.getElementById('btn-send-notif');

    if (!modal) return;

    btnOpen?.addEventListener('click',  () => modal.classList.add('open'));
    btnClose?.addEventListener('click', closeModal);
    btnCancel?.addEventListener('click', closeModal);
    modal.addEventListener('click', e => { if (e.target === modal) closeModal(); });
    btnSend?.addEventListener('click', handleSend);
}

function closeModal() {
    const modal = document.getElementById('send-modal');
    if (modal) modal.classList.remove('open');
    ['notif-recipient', 'notif-type', 'notif-title', 'notif-message'].forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        el.value = el.tagName === 'SELECT' ? (el.options[0]?.value || '') : '';
    });
}

// ─── Send notification ─────────────────────────────────────────────────────
async function handleSend() {
    const userId  = document.getElementById('notif-recipient')?.value;
    const type    = document.getElementById('notif-type')?.value    || 'General';
    const title   = document.getElementById('notif-title')?.value?.trim();
    const message = document.getElementById('notif-message')?.value?.trim();

    if (!userId)  { showToast('Please select a recipient.',  'error'); return; }
    if (!title)   { showToast('Please enter a title.',       'error'); return; }
    if (!message) { showToast('Please enter a message.',     'error'); return; }

    const btnSend = document.getElementById('btn-send-notif');
    if (btnSend) {
        btnSend.disabled = true;
        btnSend.innerHTML = '<i class="ph ph-circle-notch"></i> Sending…';
    }

    try {
        // Get the logged-in staff's clinicid from session (set by app.js → renderSidebar)
        let clinicId = null;
        try {
            const session = JSON.parse(localStorage.getItem('carelyo_admin_session') || '{}');
            clinicId = session.clinicid || null;
        } catch (_) { /* session parse failed — clinicid stays null */ }

        // ── Step 1: INSERT into NOTIFICATION table ────────────────────────
        // Columns used (all exist in the schema):
        //   userid    → NOTIFICATION.userid  (FK → USER.userid)
        //   title     → NOTIFICATION.title   (text)
        //   message   → NOTIFICATION.message (text)
        //   type      → NOTIFICATION.type    (text, stored lowercase)
        //   is_read   → NOTIFICATION.is_read (boolean, starts false)
        //   clinicid  → NOTIFICATION.clinicid (FK → CLINIC.clinicid)
        // Auto-set by DB: notificationid (identity), created_at (now())
        const { error: insertError } = await window.supabaseClient
            .from('NOTIFICATION')
            .insert({
                userid:   parseInt(userId, 10),
                title,
                message,
                type:     type.toLowerCase(),
                is_read:  false,
                clinicid: clinicId
            });

        if (insertError) {
            console.error('Supabase INSERT error:', insertError);
            showToast(`Failed to save: ${insertError.message}`, 'error');
            return;
        }

        // ── Step 2: FCM push via Supabase Edge Function ───────────────────
        try {
            const resp = await fetch(FCM_EDGE_FUNCTION_URL, {
                method:  'POST',
                headers: {
                    'Content-Type':  'application/json',
                    'Authorization': `Bearer ${supabaseKey}`
                },
                body: JSON.stringify({
                    userId: parseInt(userId, 10),
                    title,
                    message
                })
            });

            if (!resp.ok) {
                const errText = await resp.text();
                console.warn('FCM Edge Function non-fatal warning:', errText);
            } else {
                const result = await resp.json();
                console.log(`FCM push sent to user_${userId}:`, result.fcmMessageId);
            }
        } catch (fcmErr) {
            console.warn('FCM network error (non-fatal — DB row already saved):', fcmErr);
        }

        showToast('Notification sent successfully!', 'success');
        closeModal();
        loadNotifications();  // Refresh table to show new row

    } catch (err) {
        console.error('Unexpected error sending notification:', err);
        showToast('Unexpected error. Please try again.', 'error');
    } finally {
        if (btnSend) {
            btnSend.disabled = false;
            btnSend.innerHTML = '<i class="ph ph-paper-plane-tilt"></i> Send';
        }
    }
}

// ─── Toast helper ─────────────────────────────────────────────────────────
function showToast(msg, type = '') {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = msg;
    toast.className   = `toast ${type} show`;
    setTimeout(() => { toast.className = 'toast'; }, 3500);
}

// ─── Date + time formatter ─────────────────────────────────────────────────
// Output: "2026-09-05\n10:00"  (stacked, matching reference)
function formatDateTime(dateString) {
    if (!dateString) return '—';
    const d    = new Date(dateString);
    const yyyy = d.getFullYear();
    const mm   = String(d.getMonth() + 1).padStart(2, '0');
    const dd   = String(d.getDate()).padStart(2, '0');
    const hh   = String(d.getHours()).padStart(2, '0');
    const min  = String(d.getMinutes()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}<br><span style="color:#94a3b8">${hh}:${min}</span>`;
}
