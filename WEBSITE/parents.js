document.addEventListener('DOMContentLoaded', () => {
    loadParents();

    document.getElementById('search-input').addEventListener('input', (e) => {
        loadParents(e.target.value);
    });
});

async function loadParents(searchQuery = '') {
    const tableBody = document.getElementById('parents-table-body');
    const infoSpan = document.getElementById('pagination-info');

    try {
        // Build query
        // We select users and a count of their children
        let query = window.supabaseClient
            .from('USER')
            .select('*, CHILD(count)');

        if (searchQuery) {
            query = query.or(`full_name.ilike.%${searchQuery}%,email.ilike.%${searchQuery}%`);
        }

        const { data, error } = await query;

        if (error) {
            console.error("Error fetching parents:", error);
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Failed to load data</td></tr>`;
            return;
        }

        infoSpan.innerText = `Showing ${data.length} parent accounts`;

        if (data.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center;">No parent accounts found</td></tr>`;
            return;
        }

        tableBody.innerHTML = '';
        data.forEach(parent => {
            const childrenCount = parent.CHILD && parent.CHILD[0] ? parent.CHILD[0].count : 0;
            // Assuming status might be derived or hardcoded if not in schema. Schema has no status for user. 
            // Mocking 'Active' based on presence.
            const statusHtml = `<span class="badge badge-success">Active</span>`;
            
            const html = `
                <tr>
                    <td style="font-weight: 500;">${parent.full_name || 'N/A'}</td>
                    <td style="color: var(--text-muted);">${parent.email}</td>
                    <td>${childrenCount}</td>
                    <td>${formatDate(parent.created_at)}</td>
                    <td>${statusHtml}</td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn" title="View"><i class="ph ph-eye"></i></button>
                            <button class="action-btn" title="Edit"><i class="ph ph-pencil-simple"></i></button>
                            <button class="action-btn delete" title="Delete"><i class="ph ph-trash"></i></button>
                        </div>
                    </td>
                </tr>
            `;
            tableBody.insertAdjacentHTML('beforeend', html);
        });

    } catch (err) {
        console.error("Unexpected error:", err);
    }
}
