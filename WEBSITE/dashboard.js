document.addEventListener('DOMContentLoaded', async () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) {
        window.location.href = 'login.html';
        return;
    }
    let userSession = JSON.parse(sessionData);
    let clinicId = userSession.clinicid || (userSession.clinic ? userSession.clinic.clinicid : null);

    // If clinicId is not yet cached in session, resolve it from CLINIC_STAFF
    if (!clinicId && userSession.userid && window.supabaseClient) {
        try {
            const { data: staffData } = await window.supabaseClient
                .from('CLINIC_STAFF')
                .select('*')
                .eq('userid', userSession.userid)
                .maybeSingle();

            if (staffData && staffData.clinicid) {
                clinicId = staffData.clinicid;
                userSession.clinicid = clinicId;
                localStorage.setItem('carelyo_admin_session', JSON.stringify(userSession));
            }
        } catch (e) {
            console.warn("Could not resolve clinicId from CLINIC_STAFF:", e);
        }
    }

    setupHeader(userSession);
    loadDashboardData(clinicId, userSession);
});

function getInitials(name) {
    if (!name) return 'ST';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
}

function setupHeader(userSession) {
    // Format current date: e.g. "Monday, 7 September 2026"
    const dateOptions = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' };
    const todayFormatted = new Date().toLocaleDateString('en-GB', dateOptions);
    const dateEl = document.getElementById('overview-date');
    if (dateEl) dateEl.innerText = todayFormatted;

    // Staff Name and Avatar Badge
    const staffName = userSession.full_name || 'Staff User';
    const nameEl = document.getElementById('header-user-name');
    const avatarEl = document.getElementById('header-user-avatar');
    if (nameEl) nameEl.innerText = staffName;
    if (avatarEl) avatarEl.innerText = getInitials(staffName);
}

async function loadDashboardData(clinicId, userSession) {
    const today = new Date().toISOString().split('T')[0];

    try {
        // 1. Stat: Today's Appointments
        let apptCountQuery = window.supabaseClient
            .from('APPOINTMENT')
            .select('*', { count: 'exact', head: true })
            .eq('appointment_date', today);
        if (clinicId) apptCountQuery = apptCountQuery.eq('clinicid', clinicId);

        const { count: apptTodayCount } = await apptCountQuery;
        document.getElementById('stat-appointments-today').innerText = (apptTodayCount || 0).toLocaleString();

        // 2. Stat: Registered Children at this clinic (via CLINIC_PATIENT)
        let childrenCount = 0;
        let clinicChildIds = [];
        let clinicUserIds = [];

        if (clinicId) {
            try {
                const { data: clinicPatients, error: cpErr } = await window.supabaseClient
                    .from('CLINIC_PATIENT')
                    .select('childid, userid')
                    .eq('clinicid', clinicId);

                if (!cpErr && clinicPatients && clinicPatients.length > 0) {
                    clinicChildIds = [...new Set(clinicPatients.map(p => p.childid).filter(Boolean))];
                    clinicUserIds = [...new Set(clinicPatients.map(p => p.userid).filter(Boolean))];
                    childrenCount = clinicChildIds.length;
                }
            } catch (e) {
                console.warn("Could not fetch from CLINIC_PATIENT:", e);
            }

            // Fallback: If CLINIC_PATIENT has no rows yet, check APPOINTMENT for this clinic
            if (childrenCount === 0) {
                const { data: clinicAppts } = await window.supabaseClient
                    .from('APPOINTMENT')
                    .select('childid, parentid')
                    .eq('clinicid', clinicId);

                if (clinicAppts && clinicAppts.length > 0) {
                    clinicChildIds = [...new Set(clinicAppts.map(c => c.childid).filter(Boolean))];
                    clinicUserIds = [...new Set(clinicAppts.map(c => c.parentid).filter(Boolean))];
                    childrenCount = clinicChildIds.length;
                }
            }
        }

        if (childrenCount === 0 && !clinicId) {
            // Fallback to overall registered children count
            const { count: totalChildren } = await window.supabaseClient
                .from('CHILD')
                .select('childid', { count: 'exact', head: true });
            childrenCount = totalChildren || 0;
        }
        document.getElementById('stat-registered-children').innerText = childrenCount.toLocaleString();

        // 3. Stat: Overdue Vaccinations (filtered by this clinic's children)
        let overdueQuery = window.supabaseClient
            .from('CHILD_VACCINE')
            .select('childvaccineid', { count: 'exact', head: true })
            .ilike('status', 'overdue');

        if (clinicId && clinicChildIds.length > 0) {
            overdueQuery = overdueQuery.in('childid', clinicChildIds);
        }
        const { count: overdueCount } = await overdueQuery;
        const totalOverdue = overdueCount || 0;
        document.getElementById('stat-overdue-vaccines').innerText = totalOverdue.toLocaleString();

        // 4. Attention Required — check Appointments, Vaccinations, Notifications
        await loadAttentionBanner(clinicId, clinicChildIds, totalOverdue);

        // 5. Today's Schedule List
        await loadTodaySchedule(clinicId, today);

    } catch (err) {
        console.error("Error loading dashboard data:", err);
    }
}

// ─── Attention Required Banner ──────────────────────────────────────────────
// Queries three problem areas against the live schema:
//   • APPOINTMENT  — status = 'pending'  (unconfirmed appointments)
//   • CHILD_VACCINE — status ilike 'overdue' (already computed, passed in)
//   • NOTIFICATION  — is_read = false  (unread notifications for this clinic)
// Each area that has issues renders a clickable red bullet linking to its page.
// If all three are clear, shows a single green "All clear" message.
async function loadAttentionBanner(clinicId, clinicChildIds, overdueVaccines) {
    const list = document.getElementById('attention-issues-list');
    if (!list) return;

    const issues = [];   // { message, href, icon }
    const clears = [];   // strings for areas that are fine

    try {
        // ── 1. Pending / unconfirmed appointments ─────────────────────────
        // APPOINTMENT.status = 'pending' means not yet confirmed by staff
        let pendingApptQuery = window.supabaseClient
            .from('APPOINTMENT')
            .select('appid', { count: 'exact', head: true })
            .ilike('status', 'pending');
        if (clinicId) pendingApptQuery = pendingApptQuery.eq('clinicid', clinicId);

        const { count: pendingCount } = await pendingApptQuery;
        const totalPending = pendingCount || 0;

        if (totalPending > 0) {
            issues.push({
                icon: 'ph-calendar-x',
                message: `${totalPending} appointment${totalPending > 1 ? 's' : ''} pending confirmation`,
                href: 'appointments.html'
            });
        } else {
            clears.push('appointments');
        }

        // ── 2. Overdue vaccinations ───────────────────────────────────────
        // CHILD_VACCINE.status = 'overdue' — count already computed by loadDashboardData
        if (overdueVaccines > 0) {
            issues.push({
                icon: 'ph-syringe',
                message: `${overdueVaccines} overdue vaccination dose${overdueVaccines > 1 ? 's' : ''} — children need follow-up`,
                href: 'vaccinations.html'
            });
        } else {
            clears.push('vaccinations');
        }

        // ── 3. Unread notifications ───────────────────────────────────────
        // NOTIFICATION.is_read = false — unread rows for this clinic
        let unreadNotifQuery = window.supabaseClient
            .from('NOTIFICATION')
            .select('notificationid', { count: 'exact', head: true })
            .eq('is_read', false);
        if (clinicId) unreadNotifQuery = unreadNotifQuery.eq('clinicid', clinicId);

        const { count: unreadCount } = await unreadNotifQuery;
        const totalUnread = unreadCount || 0;

        if (totalUnread > 0) {
            issues.push({
                icon: 'ph-bell-ringing',
                message: `${totalUnread} unread notification${totalUnread > 1 ? 's' : ''} awaiting review`,
                href: 'notifications.html'
            });
        } else {
            clears.push('notifications');
        }

    } catch (err) {
        console.warn('Could not fully load attention banner:', err);
    }

    // ── Render ─────────────────────────────────────────────────────────────
    list.innerHTML = '';

    if (issues.length === 0) {
        // All three areas are clear — show single green message
        list.innerHTML = `
            <div class="attention-issue-row all-clear">
                <i class="ph ph-check-circle attention-issue-icon"></i>
                <span>All clear — no issues in appointments, vaccinations, or notifications</span>
            </div>`;
    } else {
        issues.forEach(({ icon, message, href }) => {
            const row = document.createElement('div');
            row.className = 'attention-issue-row';
            row.innerHTML = `
                <i class="ph ${icon} attention-issue-icon"></i>
                <span>• ${message}</span>
                <i class="ph ph-arrow-right" style="margin-left:auto;font-size:12px;color:#94a3b8;"></i>`;
            row.addEventListener('click', () => window.location.href = href);
            list.appendChild(row);
        });
    }
}

async function loadTodaySchedule(clinicId, today) {

    const listContainer = document.getElementById('today-schedule-list');
    if (!listContainer) return;

    try {
        let scheduleQuery = window.supabaseClient
            .from('APPOINTMENT')
            .select(`
                appid,
                appointment_time,
                purpose,
                status,
                CHILD (
                    childid,
                    full_name
                )
            `)
            .eq('appointment_date', today)
            .order('appointment_time', { ascending: true });

        if (clinicId) scheduleQuery = scheduleQuery.eq('clinicid', clinicId);

        let { data: appointments, error } = await scheduleQuery;

        // If no appointments for today, show most recent ones so schedule isn't empty
        if (!appointments || appointments.length === 0) {
            let fallbackQuery = window.supabaseClient
                .from('APPOINTMENT')
                .select(`
                    appid,
                    appointment_time,
                    purpose,
                    status,
                    CHILD (
                        childid,
                        full_name
                    )
                `)
                .order('appointment_date', { ascending: false })
                .order('appointment_time', { ascending: true })
                .limit(5);

            if (clinicId) fallbackQuery = fallbackQuery.eq('clinicid', clinicId);
            const { data: fallbackData } = await fallbackQuery;
            appointments = fallbackData || [];
        }

        if (appointments.length === 0) {
            listContainer.innerHTML = `
                <div style="padding: 24px; text-align: center; color: #64748b; font-size: 14px;">
                    No appointments scheduled for today.
                </div>
            `;
            return;
        }

        listContainer.innerHTML = '';
        appointments.forEach(appt => {
            // Child name is the primary label (CHILD.full_name via FK APPOINTMENT.childid)
            const childName = appt.CHILD ? appt.CHILD.full_name : 'Unknown Child';

            // Time from APPOINTMENT.appointment_time (time without time zone) e.g. "08:30:00"
            const timeStr = appt.appointment_time ? appt.appointment_time.slice(0, 5) : '—';

            // Meta line: purpose only (schema: APPOINTMENT.purpose text)
            const metaStr = appt.purpose || 'General Appointment';

            // Status pill mapping — covers all realistic APPOINTMENT.status values
            const rawStatus = (appt.status || 'Scheduled').trim();
            const lowerStatus = rawStatus.toLowerCase();
            let pillClass = 'status-scheduled';
            if      (lowerStatus === 'completed')                          pillClass = 'status-completed';
            else if (lowerStatus === 'confirmed')                          pillClass = 'status-confirmed';
            else if (lowerStatus === 'in progress' || lowerStatus === 'inprogress') pillClass = 'status-inprogress';
            else if (lowerStatus === 'cancelled'  || lowerStatus === 'canceled')    pillClass = 'status-cancelled';
            else if (lowerStatus === 'upcoming'   || lowerStatus === 'scheduled')   pillClass = 'status-scheduled';

            const rowHtml = `
                <div class="schedule-row" onclick="window.location.href='appointments.html'" style="cursor:pointer;">
                    <div class="schedule-time">${timeStr}</div>
                    <div class="schedule-details">
                        <div class="schedule-patient-name">${childName}</div>
                        <div class="schedule-meta">${metaStr}</div>
                    </div>
                    <div class="schedule-status-col">
                        <span class="status-pill ${pillClass}">${rawStatus}</span>
                        <i class="ph ph-caret-right schedule-chevron"></i>
                    </div>
                </div>
            `;
            listContainer.insertAdjacentHTML('beforeend', rowHtml);
        });

    } catch (err) {
        console.error("Error loading today's schedule:", err);
        listContainer.innerHTML = `
            <div style="padding: 24px; text-align: center; color: #ef4444; font-size: 14px;">
                Unable to load schedule.
            </div>
        `;
    }
}
