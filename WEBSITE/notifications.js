document.addEventListener('DOMContentLoaded', () => {
    loadNotifications();

    const searchInput = document.getElementById('search-input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            loadNotifications(e.target.value);
        });
    }
});

async function loadNotifications(searchQuery = '') {
    const tableBody = document.getElementById('notifications-table-body');
    if (!tableBody) return;

    try {
        let query = window.supabaseClient
            .from('NOTIFICATION')
            .select(`
                *,
                USER (full_name)
            `)
            .order('created_at', { ascending: false });

        const { data, error } = await query;

        if (error) {
            console.error("Error fetching notifications:", error);
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Failed to load notifications</td></tr>`;
            return;
        }

        let notifications = data || [];
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            notifications = notifications.filter(n => 
                (n.title && n.title.toLowerCase().includes(q)) ||
                (n.message && n.message.toLowerCase().includes(q)) ||
                (n.type && n.type.toLowerCase().includes(q)) ||
                (n.USER && n.USER.full_name && n.USER.full_name.toLowerCase().includes(q))
            );
        }

        if (notifications.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center;">No notifications found</td></tr>`;
            return;
        }

        tableBody.innerHTML = '';
        notifications.forEach(n => {
            const recipient = n.USER ? n.USER.full_name : 'Staff';
            const readBadge = n.is_read 
                ? `<span class="badge badge-success">Read</span>` 
                : `<span class="badge badge-warning">Unread</span>`;

            const html = `
                <tr>
                    <td style="font-weight: 600;">${n.title || 'Notice'}</td>
                    <td style="color: var(--text-muted); max-width: 320px;">${n.message || '-'}</td>
                    <td><span class="badge badge-primary">${n.type || 'General'}</span></td>
                    <td>${recipient}</td>
                    <td>${formatDate(n.created_at)}</td>
                    <td>${readBadge}</td>
                </tr>
            `;
            tableBody.insertAdjacentHTML('beforeend', html);
        });

    } catch (err) {
        console.error("Unexpected error loading notifications:", err);
        tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Error loading data</td></tr>`;
    }
}
