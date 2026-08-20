let currentPage = 1;
const pageSize = 10;
let totalRecords = 0;

document.addEventListener('DOMContentLoaded', () => {
    loadParents();

    document.getElementById('search-input').addEventListener('input', (e) => {
        currentPage = 1; // Reset to page 1 on new search
        loadParents(e.target.value);
    });

    document.getElementById('parent-form').addEventListener('submit', saveParent);

    document.getElementById('prev-page-btn').addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            loadParents(document.getElementById('search-input').value);
        }
    });

    document.getElementById('next-page-btn').addEventListener('click', () => {
        if (currentPage * pageSize < totalRecords) {
            currentPage++;
            loadParents(document.getElementById('search-input').value);
        }
    });
});

async function loadParents(searchQuery = '') {
    const tableBody = document.getElementById('parents-table-body');
    const infoSpan = document.getElementById('pagination-info');

    try {
        let query = window.supabaseClient
            .from('USER')
            .select('*, CHILD(count)', { count: 'exact' })
            .ilike('role', 'parent');

        if (searchQuery) {
            query = query.or(`full_name.ilike.%${searchQuery}%,email.ilike.%${searchQuery}%`);
        }

        // Apply pagination
        const from = (currentPage - 1) * pageSize;
        const to = from + pageSize - 1;
        query = query.range(from, to).order('created_at', { ascending: false });

        const { data, count, error } = await query;

        if (error) {
            console.error("Error fetching parents:", error);
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: red;">Failed to load data</td></tr>`;
            return;
        }

        totalRecords = count || 0;
        
        // Update pagination UI
        const startItem = totalRecords === 0 ? 0 : from + 1;
        const endItem = Math.min(to + 1, totalRecords);
        infoSpan.innerText = `Showing ${startItem}-${endItem} of ${totalRecords} parent accounts`;
        document.getElementById('page-number-btn').innerText = currentPage;

        if (data.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center;">No parent accounts found</td></tr>`;
            return;
        }

        tableBody.innerHTML = '';
        data.forEach(parent => {
            const childrenCount = parent.CHILD && parent.CHILD[0] ? parent.CHILD[0].count : 0;
            const statusText = parent.status || 'Active';
            const statusLower = statusText.trim().toLowerCase();
            let badgeClass = 'badge-success'; // green
            if (statusLower === 'unactive' || statusLower === 'inactive') {
                badgeClass = 'badge-danger'; // red
            }
            const statusHtml = `<span class="badge ${badgeClass}">${statusText}</span>`;
            
            const html = `
                <tr>
                    <td style="font-weight: 500;">${parent.full_name || 'N/A'}</td>
                    <td style="color: var(--text-muted);">${parent.email}</td>
                    <td>${childrenCount}</td>
                    <td>${formatDate(parent.created_at)}</td>
                    <td>${statusHtml}</td>
                    <td>
                        <div class="action-buttons">
                            <button class="action-btn" title="Edit" onclick="openModal(${parent.userid})"><i class="ph ph-pencil-simple"></i></button>
                            <button class="action-btn delete" title="Delete" onclick="deleteParent(${parent.userid})"><i class="ph ph-trash"></i></button>
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

async function openModal(id = null) {
    const modal = document.getElementById('parent-modal');
    const form = document.getElementById('parent-form');
    const title = document.getElementById('modal-title');
    const passwordLabel = document.getElementById('password-label');
    const passwordInput = document.getElementById('parent-password');
    
    form.reset();
    document.getElementById('parent-id').value = '';

    if (id) {
        title.innerText = 'Edit Parent';
        passwordLabel.innerText = 'Password (leave blank to keep current)';
        passwordInput.required = false;

        // Fetch parent details
        try {
            const { data, error } = await window.supabaseClient
                .from('USER')
                .select('*')
                .eq('userid', id)
                .single();
            
            if (error) throw error;
            if (data) {
                document.getElementById('parent-id').value = data.userid;
                document.getElementById('parent-name').value = data.full_name || '';
                document.getElementById('parent-email').value = data.email || '';
                document.getElementById('parent-phone').value = data.phone_number || '';
            }
        } catch (err) {
            console.error('Error fetching parent details:', err);
            alert('Could not fetch details.');
            return;
        }
    } else {
        title.innerText = 'Add Parent';
        passwordLabel.innerText = 'Password *';
        passwordInput.required = true;
    }

    modal.style.display = 'flex';
}

function closeModal() {
    document.getElementById('parent-modal').style.display = 'none';
}

async function saveParent(e) {
    e.preventDefault();
    const btn = document.getElementById('save-parent-btn');
    btn.disabled = true;
    btn.innerText = 'Saving...';

    const id = document.getElementById('parent-id').value;
    const fullName = document.getElementById('parent-name').value;
    const email = document.getElementById('parent-email').value;
    const phone = document.getElementById('parent-phone').value;
    const password = document.getElementById('parent-password').value;

    const payload = {
        full_name: fullName,
        email: email,
        phone_number: phone,
        role: 'parent'
    };

    if (password) {
        payload.password = password; 
    }

    try {
        if (id) {
            // Update existing
            const { error } = await window.supabaseClient
                .from('USER')
                .update(payload)
                .eq('userid', id);
            
            if (error) throw error;
            alert('Parent updated successfully!');
        } else {
            // Insert new
            const { error } = await window.supabaseClient
                .from('USER')
                .insert([payload]);
            
            if (error) throw error;
            alert('Parent added successfully!');
        }
        
        closeModal();
        loadParents(document.getElementById('search-input').value);
    } catch (err) {
        console.error('Error saving parent:', err);
        alert(err.message || 'Error saving parent. Make sure the email is unique.');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Save';
    }
}

async function deleteParent(id) {
    if (!confirm('Are you sure you want to delete this parent account? This may fail if there are linked children or appointments.')) {
        return;
    }

    try {
        const { error } = await window.supabaseClient
            .from('USER')
            .delete()
            .eq('userid', id);
        
        if (error) throw error;
        alert('Parent deleted successfully!');
        loadParents(document.getElementById('search-input').value);
    } catch (err) {
        console.error('Error deleting parent:', err);
        alert(err.message || 'Error deleting parent. They might have dependent records like children or appointments.');
    }
}
