/* ============================================================
   appointments.js — Carelyo Staff Portal
   Schema used (APPOINTMENT table):
     appid (PK), parentid FK→USER, childid FK→CHILD,
     appointment_date (date), appointment_time (time),
     purpose (text), notes (text), status (text), clinicid FK→CLINIC
   Status enum: Scheduled → Completed | Cancelled | No-Show
   ============================================================ */

let allAppointments = [];
let parentsData     = [];
let currentNotesApptId = null;
let clinicId        = null;

// ─── Init ──────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) { window.location.href = 'login.html'; return; }
    const userSession = JSON.parse(sessionData);
    clinicId = userSession.clinicid || null;

    wireModals();
    await Promise.all([loadAppointments(), loadParentsForDropdown()]);
});

// ─── Load appointments ─────────────────────────────────────────────────────
async function loadAppointments() {
    const container = document.getElementById('appointments-container');
    container.innerHTML = `<div style="text-align:center;padding:40px;color:#64748b;">Loading appointments…</div>`;

    try {
        // Query APPOINTMENT joined with USER (parent name) and CHILD (child name)
        // Ordered ascending so today shows first, then future dates
        let query = window.supabaseClient
            .from('APPOINTMENT')
            .select(`
                appid,
                appointment_date,
                appointment_time,
                purpose,
                notes,
                status,
                parentid,
                childid,
                USER   ( full_name ),
                CHILD  ( full_name )
            `)
            .order('appointment_date', { ascending: true })
            .order('appointment_time', { ascending: true });

        if (clinicId) query = query.eq('clinicid', clinicId);

        const { data, error } = await query;
        if (error) throw error;

        allAppointments = data || [];
        renderByDate(allAppointments);

    } catch (err) {
        console.error('Error loading appointments:', err);
        container.innerHTML = `<div style="text-align:center;padding:40px;color:#ef4444;">Failed to load appointments.</div>`;
    }
}

// ─── Group by date and render ──────────────────────────────────────────────
function renderByDate(appointments) {
    const container = document.getElementById('appointments-container');

    if (appointments.length === 0) {
        container.innerHTML = `<div style="text-align:center;padding:40px;color:#64748b;">No appointments found.</div>`;
        return;
    }

    // Group appointments by appointment_date (ISO string)
    const groups = {};
    appointments.forEach(appt => {
        const key = appt.appointment_date || 'Unknown';
        if (!groups[key]) groups[key] = [];
        groups[key].push(appt);
    });

    const todayISO = new Date().toISOString().split('T')[0];
    container.innerHTML = '';

    Object.entries(groups).forEach(([dateKey, appts]) => {
        const isToday  = dateKey === todayISO;
        const count    = appts.length;
        const dateLabel = formatDateLabel(dateKey);

        const section = document.createElement('div');
        section.className = 'date-section';

        section.innerHTML = `
            <div class="date-section-header">
                <div class="date-section-title">
                    <span>${dateLabel}</span>
                    ${isToday ? '<span class="today-badge">Today</span>' : ''}
                </div>
                <span class="date-appt-count">${count} appointment${count !== 1 ? 's' : ''}</span>
            </div>
            <table class="appt-table">
                <thead>
                    <tr>
                        <th>Time</th>
                        <th>Child</th>
                        <th>Parent</th>
                        <th>Purpose</th>
                        <th>Status</th>
                        <th>Update</th>
                        <th>Notes</th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>`;

        container.appendChild(section);

        const tbody = section.querySelector('tbody');
        appts.forEach(appt => tbody.appendChild(buildRow(appt)));
    });
}

// ─── Build a single table row ──────────────────────────────────────────────
function buildRow(appt) {
    const timeStr    = appt.appointment_time ? appt.appointment_time.slice(0, 5) : '—';
    const childName  = appt.CHILD ? appt.CHILD.full_name : '—';
    const parentName = appt.USER  ? appt.USER.full_name  : '—';
    const purpose    = appt.purpose || '—';
    const rawStatus  = (appt.status || 'Scheduled').trim();
    const lowerStatus = rawStatus.toLowerCase();

    // Terminal statuses — no further update needed
    const isTerminal = ['completed', 'cancelled', 'no-show'].includes(lowerStatus);

    // Status pill
    const pillClass  = getStatusPillClass(lowerStatus);
    const statusPill = `<span class="appt-status-pill ${pillClass}">${rawStatus}</span>`;

    // Update dropdown — only shown for non-terminal statuses
    // Enum: Scheduled → Completed | Cancelled | No-Show
    const updateCell = isTerminal
        ? `<span class="update-dash">—</span>`
        : `<select class="update-select" onchange="updateStatus(${appt.appid}, this.value, this)">
               <option value="" disabled selected>Update…</option>
               <option value="Completed">Completed</option>
               <option value="Cancelled">Cancelled</option>
               <option value="No-Show">No-Show</option>
           </select>`;

    // Notes button — "View" if notes exist, "Add" if empty
    const hasNotes = appt.notes && appt.notes.trim().length > 0;
    const notesBtn = `
        <button class="notes-btn"
                onclick="openNotesModal(${appt.appid}, '${esc(childName)}', '${esc(appt.appointment_date || '')}')">
            <i class="ph ph-note-pencil"></i>
            ${hasNotes ? 'View' : 'Add'}
        </button>`;

    const tr = document.createElement('tr');
    tr.id = `appt-row-${appt.appid}`;
    tr.innerHTML = `
        <td class="appt-time-col">${timeStr}</td>
        <td class="appt-child-name">${childName}</td>
        <td class="appt-parent-name">${parentName}</td>
        <td class="appt-purpose-col">${purpose}</td>
        <td id="status-cell-${appt.appid}">${statusPill}</td>
        <td id="update-cell-${appt.appid}">${updateCell}</td>
        <td>${notesBtn}</td>
    `;
    return tr;
}

// ─── Status pill CSS class ─────────────────────────────────────────────────
function getStatusPillClass(s) {
    switch (s) {
        case 'completed':   return 'appt-s-completed';
        case 'cancelled':   return 'appt-s-cancelled';
        case 'no-show':     return 'appt-s-noshow';
        case 'pending':     return 'appt-s-pending';
        case 'confirmed':   return 'appt-s-confirmed';
        case 'in progress': return 'appt-s-inprogress';
        default:            return 'appt-s-scheduled';  // Scheduled
    }
}

// ─── Escape string for inline onclick attribute ────────────────────────────
function esc(str) {
    return (str || '').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

// ─── Update appointment status inline ─────────────────────────────────────
// Updates APPOINTMENT.status in Supabase, then updates DOM without full reload
async function updateStatus(appId, newStatus, selectEl) {
    if (!newStatus) return;
    selectEl.disabled = true;

    try {
        const { error } = await window.supabaseClient
            .from('APPOINTMENT')
            .update({ status: newStatus })
            .eq('appid', appId);

        if (error) throw error;

        const lower     = newStatus.toLowerCase();
        const pillClass = getStatusPillClass(lower);
        const isTerminal = ['completed', 'cancelled', 'no-show'].includes(lower);

        // Update status pill cell
        const statusCell = document.getElementById(`status-cell-${appId}`);
        if (statusCell) {
            statusCell.innerHTML = `<span class="appt-status-pill ${pillClass}">${newStatus}</span>`;
        }

        // Replace update dropdown with "—" for terminal statuses
        const updateCell = document.getElementById(`update-cell-${appId}`);
        if (updateCell && isTerminal) {
            updateCell.innerHTML = `<span class="update-dash">—</span>`;
        } else if (updateCell) {
            selectEl.disabled = false;
            selectEl.value = '';
        }

        // Sync local data
        const local = allAppointments.find(a => a.appid === appId);
        if (local) local.status = newStatus;

        showToast('Status updated.', 'success');

    } catch (err) {
        console.error('Error updating status:', err);
        showToast('Failed to update status.', 'error');
        selectEl.disabled = false;
        selectEl.value = '';
    }
}

// ─── Notes Modal ───────────────────────────────────────────────────────────
function openNotesModal(appId, childName, apptDate) {
    currentNotesApptId = appId;
    document.getElementById('notes-modal-title').textContent = `Visit Notes — ${childName}`;

    const appt = allAppointments.find(a => a.appid === appId);
    const existingNotes = (appt && appt.notes) ? appt.notes.trim() : '';

    const existingContainer = document.getElementById('notes-existing-container');
    if (existingNotes) {
        // Show the existing notes with date as meta header
        existingContainer.innerHTML = `
            <div class="notes-existing">
                <div class="notes-existing-meta">${apptDate}</div>
                <div class="notes-existing-text">${existingNotes}</div>
            </div>`;
    } else {
        existingContainer.innerHTML = '';
    }

    document.getElementById('notes-textarea').value = '';
    document.getElementById('notes-modal').classList.add('open');
}

function closeNotesModal() {
    document.getElementById('notes-modal').classList.remove('open');
    currentNotesApptId = null;
}

async function saveNotes() {
    if (!currentNotesApptId) return;
    const btn     = document.getElementById('save-notes-btn');
    const newNote = document.getElementById('notes-textarea').value.trim();

    // Nothing typed — just close
    if (!newNote) { closeNotesModal(); return; }

    btn.disabled    = true;
    btn.textContent = 'Saving…';

    try {
        // Append to existing APPOINTMENT.notes or set fresh
        const appt     = allAppointments.find(a => a.appid === currentNotesApptId);
        const existing = (appt && appt.notes) ? appt.notes.trim() : '';
        const combined = existing ? `${existing}\n\n${newNote}` : newNote;

        const { error } = await window.supabaseClient
            .from('APPOINTMENT')
            .update({ notes: combined })
            .eq('appid', currentNotesApptId);

        if (error) throw error;

        // Update local cache
        if (appt) appt.notes = combined;
        showToast('Notes saved.', 'success');
        closeNotesModal();

    } catch (err) {
        console.error('Error saving notes:', err);
        showToast('Failed to save notes.', 'error');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Save';
    }
}

// ─── Add Appointment Modal ─────────────────────────────────────────────────
async function loadParentsForDropdown() {
    try {
        let query = window.supabaseClient
            .from('USER')
            .select('userid, full_name, CHILD(childid, full_name)')
            .ilike('role', 'parent')
            .order('full_name', { ascending: true });

        // Narrow to clinic patients if clinicId is known
        if (clinicId) {
            const { data: cp } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .select('userid')
                .eq('clinicid', clinicId)
                .not('userid', 'is', null);
            if (cp && cp.length > 0) {
                const ids = [...new Set(cp.map(p => p.userid).filter(Boolean))];
                if (ids.length > 0) query = query.in('userid', ids);
            }
        }

        const { data, error } = await query;
        if (error) throw error;
        parentsData = data || [];

        const sel = document.getElementById('new-appt-parent');
        sel.innerHTML = '<option value="">— Select parent —</option>' +
            parentsData.map(p => `<option value="${p.userid}">${p.full_name}</option>`).join('');

    } catch (err) {
        console.error('Error loading parents for dropdown:', err);
    }
}

function onParentChange() {
    const parentId = document.getElementById('new-appt-parent').value;
    const childSel = document.getElementById('new-appt-child');

    if (!parentId) {
        childSel.innerHTML  = '<option value="">Select a parent first…</option>';
        childSel.disabled   = true;
        return;
    }

    const parent = parentsData.find(p => p.userid == parentId);
    if (!parent || !parent.CHILD || parent.CHILD.length === 0) {
        childSel.innerHTML  = '<option value="">No children found</option>';
        childSel.disabled   = true;
        return;
    }

    childSel.innerHTML = '<option value="">— Select child —</option>' +
        parent.CHILD.map(c => `<option value="${c.childid}">${c.full_name}</option>`).join('');
    childSel.disabled = false;
}

async function saveNewAppointment() {
    const date     = document.getElementById('new-appt-date').value;
    const time     = document.getElementById('new-appt-time').value;
    const parentId = document.getElementById('new-appt-parent').value;
    const childId  = document.getElementById('new-appt-child').value;
    const purpose  = document.getElementById('new-appt-purpose').value.trim();

    if (!date || !time || !parentId || !childId) {
        showToast('Please fill in Date, Time, Parent and Child.', 'error');
        return;
    }

    const btn = document.getElementById('save-add-appt');
    btn.disabled    = true;
    btn.textContent = 'Adding…';

    try {
        const payload = {
            appointment_date: date,
            appointment_time: time,
            parentid: parseInt(parentId, 10),
            childid:  parseInt(childId,  10),
            purpose:  purpose || null,
            status:   'Scheduled'   // new appointments start as Scheduled
        };
        if (clinicId) payload.clinicid = clinicId;

        // INSERT appointment and get back the new appid so we can link the notification
        const { data: newApptRows, error } = await window.supabaseClient
            .from('APPOINTMENT')
            .insert([payload])
            .select('appid');
        if (error) throw error;

        const newAppId = newApptRows && newApptRows[0] ? newApptRows[0].appid : null;

        // ── Insert NOTIFICATION to alert the parent ───────────────────────
        // NOTIFICATION columns used:
        //   userid      → parentid  (the parent who booked / owns the child)
        //   childid     → childid   (which child the appointment is for)
        //   appid       → newAppId  (FK back to the new APPOINTMENT row)
        //   clinicid    → clinicId  (the clinic that created it)
        //   title       → short heading shown in the app notification
        //   message     → full body text
        //   type        → 'appointment' (used by the app to categorise)
        //   is_read     → false  (unread; Android NotificationSyncManager picks this up)
        if (newAppId) {
            // Resolve child name for the message text
            const parent   = parentsData.find(p => p.userid == parentId);
            const child    = parent && parent.CHILD
                ? parent.CHILD.find(c => c.childid == childId)
                : null;
            const childName  = child ? child.full_name : 'your child';

            // Format date nicely: "Mon, 7 Sept 2026"
            const d       = new Date(date + 'T00:00:00');
            const days    = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
            const months  = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
            const dateLabel = `${days[d.getDay()]}, ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
            const timeLabel = time.slice(0, 5);  // "HH:MM"

            const purposeLabel = purpose || 'General Appointment';

            const notifPayload = {
                userid:   parseInt(parentId, 10),
                childid:  parseInt(childId,  10),
                appid:    newAppId,
                title:    'Appointment Scheduled',
                message:  `An appointment has been scheduled for ${childName} on ${dateLabel} at ${timeLabel} — ${purposeLabel}.`,
                type:     'appointment',
                is_read:  false
            };
            if (clinicId) notifPayload.clinicid = clinicId;

            // Non-blocking: if notification insert fails, we still show success for the appointment
            window.supabaseClient.from('NOTIFICATION').insert([notifPayload])
                .then(({ error: nErr }) => {
                    if (nErr) console.warn('Notification insert failed (appointment still saved):', nErr);
                });
        }

        showToast('Appointment added successfully!', 'success');
        closeAddModal();
        await loadAppointments();

    } catch (err) {
        console.error('Error adding appointment:', err);
        showToast(err.message || 'Failed to add appointment.', 'error');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Add Appointment';
    }
}

// ─── Modal wiring ──────────────────────────────────────────────────────────
function wireModals() {
    // Add appointment modal
    document.getElementById('btn-add-appt').addEventListener('click',    openAddModal);
    document.getElementById('close-add-modal').addEventListener('click', closeAddModal);
    document.getElementById('cancel-add-modal').addEventListener('click',closeAddModal);
    document.getElementById('save-add-appt').addEventListener('click',   saveNewAppointment);
    document.getElementById('new-appt-parent').addEventListener('change',onParentChange);

    // Notes modal
    document.getElementById('close-notes-modal').addEventListener('click',  closeNotesModal);
    document.getElementById('close-notes-footer').addEventListener('click',  closeNotesModal);
    document.getElementById('save-notes-btn').addEventListener('click',      saveNotes);

    // Set defaults
    document.getElementById('new-appt-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('new-appt-time').value = '09:00';
}

function openAddModal() {
    document.getElementById('new-appt-date').value    = new Date().toISOString().split('T')[0];
    document.getElementById('new-appt-time').value    = '09:00';
    document.getElementById('new-appt-parent').value  = '';
    document.getElementById('new-appt-child').innerHTML = '<option value="">Select a parent first…</option>';
    document.getElementById('new-appt-child').disabled  = true;
    document.getElementById('new-appt-purpose').value  = '';
    document.getElementById('add-appt-modal').classList.add('open');
}

function closeAddModal() {
    document.getElementById('add-appt-modal').classList.remove('open');
}

// ─── Toast helper ──────────────────────────────────────────────────────────
function showToast(msg, type = '') {
    const toast = document.getElementById('appt-toast');
    if (!toast) return;
    toast.textContent       = msg;
    toast.style.background  = type === 'error' ? '#ef4444' : '#10b981';
    toast.style.color       = '#fff';
    toast.style.opacity     = '1';
    setTimeout(() => { toast.style.opacity = '0'; }, 3000);
}

// ─── Date label formatter ──────────────────────────────────────────────────
// e.g. "2026-09-07" → "Mon, 7 Sept"
function formatDateLabel(dateStr) {
    if (!dateStr || dateStr === 'Unknown') return 'Unknown Date';
    const d      = new Date(dateStr + 'T00:00:00');
    const days   = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return `${days[d.getDay()]}, ${d.getDate()} ${months[d.getMonth()]}`;
}
