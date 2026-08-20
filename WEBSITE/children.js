let allChildren = [];
let filteredChildren = [];
let currentPage = 1;
const pageSize = 10;
let allParents = [];

document.addEventListener('DOMContentLoaded', () => {
    loadChildren();
    loadParentsForDropdown();

    document.getElementById('search-input').addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase();
        filteredChildren = allChildren.filter(child => {
            const childName = (child.full_name || '').toLowerCase();
            const parentName = (child.USER?.full_name || '').toLowerCase();
            return childName.includes(query) || parentName.includes(query);
        });
        currentPage = 1;
        renderChildren();
    });

    document.getElementById('prev-page-btn').addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            renderChildren();
        }
    });

    document.getElementById('next-page-btn').addEventListener('click', () => {
        if (currentPage * pageSize < filteredChildren.length) {
            currentPage++;
            renderChildren();
        }
    });

    document.getElementById('child-form').addEventListener('submit', saveChild);
});

async function loadChildren() {
    const tableBody = document.getElementById('children-table-body');
    
    try {
        const { data, error } = await window.supabaseClient
            .from('CHILD')
            .select('*, USER(full_name)')
            .order('created_at', { ascending: false });

        if (error) throw error;

        allChildren = data || [];
        filteredChildren = [...allChildren];
        currentPage = 1;
        renderChildren();

    } catch (err) {
        console.error("Error fetching children:", err);
        tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: red;">Failed to load data</td></tr>`;
    }
}

async function loadParentsForDropdown() {
    try {
        const { data, error } = await window.supabaseClient
            .from('USER')
            .select('userid, full_name')
            .ilike('role', 'parent')
            .order('full_name', { ascending: true });
            
        if (error) throw error;
        allParents = data || [];
        
        const select = document.getElementById('child-parent');
        select.innerHTML = '<option value="">Select a parent...</option>';
        allParents.forEach(p => {
            select.innerHTML += `<option value="${p.userid}">${p.full_name}</option>`;
        });
    } catch (err) {
        console.error("Error fetching parents for dropdown:", err);
    }
}

function renderChildren() {
    const tableBody = document.getElementById('children-table-body');
    const infoSpan = document.getElementById('pagination-info');

    const totalRecords = filteredChildren.length;
    const from = (currentPage - 1) * pageSize;
    const to = Math.min(from + pageSize, totalRecords);
    
    const paginatedData = filteredChildren.slice(from, to);

    const startItem = totalRecords === 0 ? 0 : from + 1;
    infoSpan.innerText = `Showing ${startItem}-${to} of ${totalRecords} child accounts`;
    document.getElementById('page-number-btn').innerText = currentPage;

    if (paginatedData.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center;">No child accounts found</td></tr>`;
        return;
    }

    tableBody.innerHTML = '';
    paginatedData.forEach(child => {
        const parentName = child.USER ? child.USER.full_name : 'Unknown';
        
        const statusText = child.status || 'Active';
        const statusLower = statusText.trim().toLowerCase();
        let badgeClass = 'badge-success'; // green
        if (statusLower === 'unactive' || statusLower === 'inactive') {
            badgeClass = 'badge-danger'; // red
        }
        
        const html = `
            <tr>
                <td style="font-weight: 500;">${child.full_name || 'N/A'}</td>
                <td style="color: var(--text-muted);">${parentName}</td>
                <td>${formatDate(child.date_of_birth)}</td>
                <td>${child.gender || 'N/A'}</td>
                <td>${child.blood_type || 'N/A'}</td>
                <td>${child.weight || 'N/A'}</td>
                <td>${child.height || 'N/A'}</td>
                <td><span class="badge ${badgeClass}">${statusText}</span></td>
                <td>
                    <div class="action-buttons">
                        <button class="action-btn" title="Edit" onclick="openModal(${child.childid})"><i class="ph ph-pencil-simple"></i></button>
                        <button class="action-btn delete" title="Delete" onclick="deleteChild(${child.childid})"><i class="ph ph-trash"></i></button>
                    </div>
                </td>
            </tr>
        `;
        tableBody.insertAdjacentHTML('beforeend', html);
    });
}

async function openModal(id = null) {
    const modal = document.getElementById('child-modal');
    const form = document.getElementById('child-form');
    const title = document.getElementById('modal-title');
    
    form.reset();
    document.getElementById('child-id').value = '';

    if (id) {
        title.innerText = 'Edit Child';

        try {
            const { data, error } = await window.supabaseClient
                .from('CHILD')
                .select('*')
                .eq('childid', id)
                .single();
            
            if (error) throw error;
            if (data) {
                document.getElementById('child-id').value = data.childid;
                document.getElementById('child-parent').value = data.parent_id || '';
                document.getElementById('child-name').value = data.full_name || '';
                document.getElementById('child-dob').value = data.date_of_birth || '';
                document.getElementById('child-gender').value = data.gender || '';
                document.getElementById('child-blood').value = data.blood_type || '';
                document.getElementById('child-weight').value = data.weight || '';
                document.getElementById('child-height').value = data.height || '';
                document.getElementById('child-status').value = data.status || '';
            }
        } catch (err) {
            console.error('Error fetching child details:', err);
            alert('Could not fetch details.');
            return;
        }
    } else {
        title.innerText = 'Add Child';
    }

    modal.style.display = 'flex';
}

function closeModal() {
    document.getElementById('child-modal').style.display = 'none';
}

async function saveChild(e) {
    e.preventDefault();
    const btn = document.getElementById('save-child-btn');
    btn.disabled = true;
    btn.innerText = 'Saving...';

    const id = document.getElementById('child-id').value;
    const parentId = document.getElementById('child-parent').value;
    const fullName = document.getElementById('child-name').value;
    const dob = document.getElementById('child-dob').value;
    const gender = document.getElementById('child-gender').value;
    const bloodType = document.getElementById('child-blood').value;
    const weight = document.getElementById('child-weight').value;
    const height = document.getElementById('child-height').value;
    const status = document.getElementById('child-status').value;

    const payload = {
        parent_id: parentId,
        full_name: fullName,
        date_of_birth: dob,
        gender: gender,
        blood_type: bloodType,
        weight: weight,
        height: height,
        status: status
    };

    try {
        if (id) {
            const { error } = await window.supabaseClient
                .from('CHILD')
                .update(payload)
                .eq('childid', id);
            
            if (error) throw error;
            alert('Child updated successfully!');
        } else {
            const { error } = await window.supabaseClient
                .from('CHILD')
                .insert([payload]);
            
            if (error) throw error;
            alert('Child added successfully!');
        }
        
        closeModal();
        await loadChildren();
        // re-apply search filter
        const searchInput = document.getElementById('search-input');
        if (searchInput) {
            searchInput.dispatchEvent(new Event('input'));
        }
    } catch (err) {
        console.error('Error saving child:', err);
        alert(err.message || 'Error saving child.');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Save';
    }
}

async function deleteChild(id) {
    if (!confirm('Are you sure you want to delete this child account? This will also remove associated records like appointments.')) {
        return;
    }

    try {
        const { error } = await window.supabaseClient
            .from('CHILD')
            .delete()
            .eq('childid', id);
        
        if (error) throw error;
        alert('Child deleted successfully!');
        await loadChildren();
        const searchInput = document.getElementById('search-input');
        if (searchInput) {
            searchInput.dispatchEvent(new Event('input'));
        }
    } catch (err) {
        console.error('Error deleting child:', err);
        alert(err.message || 'Error deleting child.');
    }
}
