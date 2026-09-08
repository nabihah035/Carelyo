document.addEventListener('DOMContentLoaded', () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) {
        window.location.href = 'login.html';
        return;
    }
    const userSession = JSON.parse(sessionData);
    const clinicId = userSession.clinicid;

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

        // 2. Stat: Registered Children at this clinic
        let childrenCount = 0;
        if (clinicId) {
            const { data: clinicChildren } = await window.supabaseClient
                .from('APPOINTMENT')
                .select('childid')
                .eq('clinicid', clinicId);

            if (clinicChildren && clinicChildren.length > 0) {
                const uniqueIds = new Set(clinicChildren.map(c => c.childid).filter(Boolean));
                childrenCount = uniqueIds.size;
            }
        }
        if (childrenCount === 0) {
            // Fallback to overall registered children count
            const { count: totalChildren } = await window.supabaseClient
                .from('CHILD')
                .select('childid', { count: 'exact', head: true });
            childrenCount = totalChildren || 0;
        }
        document.getElementById('stat-registered-children').innerText = childrenCount.toLocaleString();

        // 3. Stat: Overdue Vaccinations
        let overdueQuery = window.supabaseClient
            .from('CHILD_VACCINE')
            .select('childvaccineid', { count: 'exact', head: true })
            .ilike('status', 'overdue');
        const { count: overdueCount } = await overdueQuery;
        const totalOverdue = overdueCount || 0;
        document.getElementById('stat-overdue-vaccines').innerText = totalOverdue.toLocaleString();

        // 4. Stat: Pending Reminders
        let pendingRemindersCount = 0;
        try {
            const { count: remCount, error: remError } = await window.supabaseClient
                .from('REMINDER')
                .select('remindid', { count: 'exact', head: true })
                .eq('is_sent', false);
            if (!remError && remCount !== null) {
                pendingRemindersCount = remCount;
            } else {
                // Try noti_status if is_sent didn't match
                const { count: altCount } = await window.supabaseClient
                    .from('REMINDER')
                    .select('remindid', { count: 'exact', head: true })
                    .ilike('noti_status', 'pending');
                pendingRemindersCount = altCount || 0;
            }
        } catch (e) {
            console.warn("Could not query REMINDER table:", e);
        }
        document.getElementById('stat-pending-reminders').innerText = pendingRemindersCount.toLocaleString();

        // 5. Attention Required Banner
        const attentionDetails = document.getElementById('attention-details');
        if (attentionDetails) {
            if (totalOverdue > 0) {
                attentionDetails.innerText = `${totalOverdue} overdue vaccination doses — children need to be contacted`;
            } else {
                attentionDetails.innerText = `All vaccination doses are up to date — no action needed`;
                const textEl = document.getElementById('attention-text');
                if (textEl) textEl.style.color = 'var(--success-color)';
            }
        }

        // 6. Today's Schedule List
        await loadTodaySchedule(clinicId, today);

    } catch (err) {
        console.error("Error loading dashboard data:", err);
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
                appointment_date,
                appointment_time,
                purpose,
                notes,
                status,
                CHILD (
                    childid,
                    full_name
                ),
                USER (
                    userid,
                    full_name
                )
            `)
            .eq('appointment_date', today)
            .order('appointment_time', { ascending: true });

        if (clinicId) scheduleQuery = scheduleQuery.eq('clinicid', clinicId);

        let { data: appointments, error } = await scheduleQuery;

        // If no appointments for today, show upcoming or recent so schedule is not completely empty
        if (!appointments || appointments.length === 0) {
            let fallbackQuery = window.supabaseClient
                .from('APPOINTMENT')
                .select(`
                    appid,
                    appointment_date,
                    appointment_time,
                    purpose,
                    notes,
                    status,
                    CHILD (
                        childid,
                        full_name
                    ),
                    USER (
                        userid,
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
            const childName = appt.CHILD ? appt.CHILD.full_name : 'Unknown Child';
            
            // Format time e.g. "08:30"
            let timeStr = '09:00';
            if (appt.appointment_time) {
                timeStr = appt.appointment_time.slice(0, 5);
            }

            // Description / meta line
            let metaParts = [];
            if (appt.purpose) metaParts.push(appt.purpose);
            if (appt.notes) metaParts.push(appt.notes);
            if (appt.USER && appt.USER.full_name) metaParts.push(`Parent: ${appt.USER.full_name}`);
            const metaStr = metaParts.length > 0 ? metaParts.join(' — ') : 'General Appointment';

            // Status styling
            const rawStatus = (appt.status || 'Scheduled').trim();
            const lowerStatus = rawStatus.toLowerCase();
            let pillClass = 'status-scheduled';
            if (lowerStatus === 'completed') pillClass = 'status-completed';
            else if (lowerStatus === 'upcoming') pillClass = 'status-upcoming';
            else if (lowerStatus === 'cancelled') pillClass = 'status-cancelled';

            const rowHtml = `
                <div class="schedule-row" onclick="window.location.href='appointments.html'" style="cursor: pointer;">
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
