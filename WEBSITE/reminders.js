document.addEventListener('DOMContentLoaded', () => {
    loadReminders();

    const searchInput = document.getElementById('search-input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            loadReminders(e.target.value);
        });
    }
});

async function loadReminders(searchQuery = '') {
    const tableBody = document.getElementById('reminders-table-body');
    if (!tableBody) return;

    try {
        let query = window.supabaseClient
            .from('REMINDER')
            .select(`
                *,
                CHILD (full_name),
                USER (full_name)
            `)
            .order('scheduled_at', { ascending: false });

        const { data, error } = await query;

        if (error) {
            console.error("Error fetching reminders:", error);
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Failed to load reminders</td></tr>`;
            return;
        }

        let reminders = data || [];
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            reminders = reminders.filter(r => 
                (r.reminder_type && r.reminder_type.toLowerCase().includes(q)) ||
                (r.noti_status && r.noti_status.toLowerCase().includes(q)) ||
                (r.CHILD && r.CHILD.full_name && r.CHILD.full_name.toLowerCase().includes(q)) ||
                (r.USER && r.USER.full_name && r.USER.full_name.toLowerCase().includes(q))
            );
        }

        if (reminders.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center;">No reminders found</td></tr>`;
            return;
        }

        tableBody.innerHTML = '';
        reminders.forEach(r => {
            const childName = r.CHILD ? r.CHILD.full_name : 'N/A';
            const parentName = r.USER ? r.USER.full_name : 'N/A';
            const scheduledAt = r.scheduled_at ? formatDate(r.scheduled_at) : 'Not scheduled';
            
            const isSent = r.is_sent;
            const dispatchBadge = isSent 
                ? `<span class="badge badge-success">Sent</span>` 
                : `<span class="badge badge-warning">Awaiting Dispatch</span>`;

            const statusText = r.noti_status || 'Pending';
            let statusBadge = `<span class="badge badge-primary">${statusText}</span>`;
            if (statusText.toLowerCase() === 'completed') statusBadge = `<span class="badge badge-success">${statusText}</span>`;
            if (statusText.toLowerCase() === 'failed') statusBadge = `<span class="badge badge-danger">${statusText}</span>`;

            const html = `
                <tr>
                    <td style="font-weight: 500;">${r.reminder_type || 'General'}</td>
                    <td>${childName}</td>
                    <td>${parentName}</td>
                    <td>${scheduledAt}</td>
                    <td>${statusBadge}</td>
                    <td>${dispatchBadge}</td>
                </tr>
            `;
            tableBody.insertAdjacentHTML('beforeend', html);
        });

    } catch (err) {
        console.error("Unexpected error loading reminders:", err);
        tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Error loading data</td></tr>`;
    }
}
