/* ============================================================
   parents.js — Carelyo Staff Portal
   Schema tables used:
     USER   (userid, full_name, email, phone_number, role, created_at, password)
     CHILD  (childid, parent_id FK→USER, full_name, date_of_birth, gender,
             blood_type, weight, height, status, created_at)
     CLINIC_PATIENT (clinic_patient_id, clinicid, userid, childid)
   ============================================================ */

let allParents   = [];
let clinicId     = null;
let currentParentId = null;   // for the Add Child modal
let existingParentsData = []; // for the "Link Existing" tab

// ─── Init ──────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) { window.location.href = 'login.html'; return; }
    clinicId = JSON.parse(sessionData).clinicid || null;

    wireModals();
    await loadParents();
    await loadExistingParentsForDropdown();

    // Live search
    document.getElementById('search-input').addEventListener('input', e => {
        const q = e.target.value.toLowerCase();
        const filtered = allParents.filter(p =>
            (p.full_name || '').toLowerCase().includes(q) ||
            (p.email     || '').toLowerCase().includes(q)
        );
        renderParentsList(filtered);
    });
});

// ─── VIEW SWITCHING ────────────────────────────────────────────────────────
function showListView() {
    document.getElementById('list-view').classList.remove('hidden');
    document.getElementById('detail-view').classList.remove('active');
}

function showDetailView() {
    document.getElementById('list-view').classList.add('hidden');
    document.getElementById('detail-view').classList.add('active');
}

// ─── LOAD PARENTS LIST ─────────────────────────────────────────────────────
// Queries USER where role='parent', joins CHILD count, filters by clinic
async function loadParents() {
    const tbody = document.getElementById('parents-tbody');
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:32px;color:#64748b;">Loading parents…</td></tr>`;

    try {
        // Build query — select USER rows with CHILD child rows for count
        let query = window.supabaseClient
            .from('USER')
            .select(`
                userid,
                full_name,
                email,
                phone_number,
                created_at,
                CHILD ( childid )
            `)
            .ilike('role', 'parent')
            .order('created_at', { ascending: true });

        // Narrow to clinic patients if clinicId is set
        if (clinicId) {
            const { data: cp } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .select('userid')
                .eq('clinicid', clinicId)
                .not('userid', 'is', null);

            let ids = cp ? [...new Set(cp.map(p => p.userid).filter(Boolean))] : [];

            // Fallback: infer from APPOINTMENT.parentid for this clinic
            if (ids.length === 0) {
                const { data: appts } = await window.supabaseClient
                    .from('APPOINTMENT')
                    .select('parentid')
                    .eq('clinicid', clinicId)
                    .not('parentid', 'is', null);
                if (appts) ids = [...new Set(appts.map(a => a.parentid).filter(Boolean))];
            }

            if (ids.length > 0) query = query.in('userid', ids);
        }

        const { data, error } = await query;
        if (error) throw error;

        allParents = data || [];
        renderParentsList(allParents);

    } catch (err) {
        console.error('Error loading parents:', err);
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:32px;color:#ef4444;">Failed to load parents.</td></tr>`;
    }
}

// ─── LOAD EXISTING PARENTS FOR DROPDOWN ────────────────────────────────────
// Fetches parents NOT already linked to this clinic
async function loadExistingParentsForDropdown() {
    const select = document.getElementById('ep-select');
    if (!select) return;

    try {
        // Get all parents
        const { data: allParentUsers, error } = await window.supabaseClient
            .from('USER')
            .select('userid, full_name, email')
            .ilike('role', 'parent')
            .order('full_name', { ascending: true });

        if (error) throw error;

        // Get already-linked parent IDs for this clinic
        let linkedIds = [];
        if (clinicId) {
            const { data: cp } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .select('userid')
                .eq('clinicid', clinicId)
                .not('userid', 'is', null);
            if (cp) linkedIds = cp.map(p => p.userid).filter(Boolean);
        }

        // Filter out already-linked parents
        existingParentsData = (allParentUsers || []).filter(
            p => !linkedIds.includes(p.userid)
        );

        if (existingParentsData.length === 0) {
            select.innerHTML = '<option value="">No unlinked parents available</option>';
            return;
        }

        select.innerHTML = '<option value="">— Select a parent —</option>' +
            existingParentsData.map(p =>
                `<option value="${p.userid}">${p.full_name} (${p.email || 'no email'})</option>`
            ).join('');

    } catch (err) {
        console.error('Error loading existing parents:', err);
        select.innerHTML = '<option value="">Error loading parents</option>';
    }
}

// ─── RENDER PARENTS TABLE ──────────────────────────────────────────────────
function renderParentsList(parents) {
    const tbody = document.getElementById('parents-tbody');

    if (parents.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:32px;color:#64748b;">No parents found.</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    parents.forEach((parent, idx) => {
        // CHILD is an array of child rows joined via parent_id FK
        const childCount = parent.CHILD ? parent.CHILD.length : 0;
        const phone      = parent.phone_number || '—';
        const email      = parent.email || '—';
        const regDate    = parent.created_at ? formatShortDate(parent.created_at) : '—';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="p-num-col">${idx + 1}</td>
            <td class="p-name-col">${parent.full_name || '—'}</td>
            <td class="p-phone-col">${phone}</td>
            <td class="p-email-col">${email}</td>
            <td class="p-date-col">${regDate}</td>
            <td><span class="child-count-badge">${childCount}</span></td>
            <td>
                <button class="view-children-btn" data-id="${parent.userid}">
                    View Children →
                </button>
            </td>
        `;

        // Click "View Children →" to open detail view
        tr.querySelector('.view-children-btn').addEventListener('click', () => {
            openDetailView(parent);
        });

        tbody.appendChild(tr);
    });
}

// ─── OPEN DETAIL VIEW ─────────────────────────────────────────────────────
// Shows the parent info card + their children table
async function openDetailView(parent) {
    currentParentId = parent.userid;

    // Fill parent info card
    document.getElementById('breadcrumb-name').textContent  = parent.full_name || '—';
    document.getElementById('detail-name').textContent      = parent.full_name || '—';
    document.getElementById('detail-phone').textContent     = parent.phone_number || '—';
    document.getElementById('detail-email').textContent     = parent.email || '—';
    document.getElementById('detail-reg').textContent       =
        parent.created_at ? `Registered ${formatLongDate(parent.created_at)}` : '';

    showDetailView();

    // Load children
    await loadChildren(parent.userid);
}

// ─── LOAD CHILDREN FOR A PARENT ────────────────────────────────────────────
// Queries CHILD where parent_id = userid (FK: CHILD.parent_id → USER.userid)
async function loadChildren(parentUserId) {
    const tbody = document.getElementById('children-tbody');
    tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:32px;color:#64748b;">Loading children…</td></tr>`;

    try {
        const { data, error } = await window.supabaseClient
            .from('CHILD')
            .select(`
                childid,
                full_name,
                date_of_birth,
                gender,
                blood_type,
                weight,
                height,
                status,
                created_at
            `)
            .eq('parent_id', parentUserId)
            .order('date_of_birth', { ascending: true });

        if (error) throw error;

        const children = data || [];

        // Update child count on info card
        document.getElementById('detail-child-count').textContent = children.length;
        const countLabel = document.querySelector('.parent-child-count-label');
        if (countLabel) countLabel.textContent = children.length === 1 ? 'registered child' : 'registered children';

        if (children.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:32px;color:#64748b;">No children registered for this parent.</td></tr>`;
            return;
        }

        tbody.innerHTML = '';
        children.forEach((child, idx) => {
            const dob    = child.date_of_birth || '—';
            const age    = child.date_of_birth ? calcAge(child.date_of_birth) : '—';
            const gender = child.gender || '—';
            const blood  = child.blood_type || '—';
            const weight = child.weight ? `${child.weight} kg` : '—';
            const height = child.height ? `${child.height} cm` : '—';

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="p-num-col">${idx + 1}</td>
                <td style="font-weight:600;color:#111827;">${child.full_name || '—'}</td>
                <td style="color:#64748b;font-size:13px;">${dob}</td>
                <td style="color:#64748b;">${age}</td>
                <td style="color:#374151;">${gender}</td>
                <td style="color:#374151;">${blood}</td>
                <td style="color:#64748b;">${weight}</td>
                <td style="color:#64748b;">${height}</td>
                <td>
                    <button class="view-children-btn" style="gap:5px;">
                        <i class="ph ph-eye" style="font-size:14px;"></i>
                        View Records
                    </button>
                </td>
            `;
            // "View Records" — navigates to health.html with childid in URL
            tr.querySelector('button').addEventListener('click', () => {
                window.location.href = `health.html?childid=${child.childid}`;
            });
            tbody.appendChild(tr);
        });

    } catch (err) {
        console.error('Error loading children:', err);
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:32px;color:#ef4444;">Failed to load children.</td></tr>`;
    }
}

// ─── ADD PARENT (Create New) ───────────────────────────────────────────────
async function saveNewParent() {
    const name     = document.getElementById('np-name').value.trim();
    const email    = document.getElementById('np-email').value.trim();
    const phone    = document.getElementById('np-phone').value.trim();
    const password = document.getElementById('np-password').value;

    if (!name || !email || !password) {
        showToast('Full Name, Email and Password are required.', 'error');
        return;
    }

    const btn = document.getElementById('save-add-parent');
    btn.disabled    = true;
    btn.textContent = 'Adding…';

    try {
        const payload = {
            full_name:    name,
            email:        email,
            phone_number: phone || null,
            password:     password,
            role:         'parent'
        };

        const { data: newRows, error } = await window.supabaseClient
            .from('USER')
            .insert([payload])
            .select('userid');

        if (error) throw error;

        // Register in CLINIC_PATIENT so they appear in this clinic's list
        if (clinicId && newRows && newRows.length > 0) {
            await window.supabaseClient.from('CLINIC_PATIENT').insert([{
                clinicid: clinicId,
                userid:   newRows[0].userid
            }]).then(({ error: cpErr }) => {
                if (cpErr) console.warn('CLINIC_PATIENT insert failed:', cpErr);
            });
        }

        showToast('Parent added successfully!', 'success');
        closeAddParentModal();
        await loadParents();
        await loadExistingParentsForDropdown();

    } catch (err) {
        console.error('Error adding parent:', err);
        showToast(err.message || 'Failed to add parent. Ensure the email is unique.', 'error');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Add Parent';
    }
}

// ─── LINK EXISTING PARENT ──────────────────────────────────────────────────
async function linkExistingParent() {
    const selectedUserId = document.getElementById('ep-select').value;

    if (!selectedUserId) {
        showToast('Please select a parent to link.', 'error');
        return;
    }

    if (!clinicId) {
        showToast('Clinic ID not found. Please re-login.', 'error');
        return;
    }

    const btn = document.getElementById('save-add-parent');
    btn.disabled    = true;
    btn.textContent = 'Linking…';

    try {
        // Insert into CLINIC_PATIENT to link the parent to this clinic
        const { error } = await window.supabaseClient
            .from('CLINIC_PATIENT')
            .insert([{
                clinicid: clinicId,
                userid:   parseInt(selectedUserId, 10)
            }]);

        if (error) {
            // Handle duplicate key error gracefully
            if (error.code === '23505') {
                showToast('This parent is already linked to your clinic.', 'error');
            } else {
                throw error;
            }
            return;
        }

        showToast('Parent linked successfully!', 'success');
        closeAddParentModal();
        await loadParents();
        await loadExistingParentsForDropdown();

    } catch (err) {
        console.error('Error linking parent:', err);
        showToast(err.message || 'Failed to link parent.', 'error');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Add Parent';
    }
}

// ─── ADD CHILD ─────────────────────────────────────────────────────────────
// Inserts into CHILD with parent_id = currentParentId
async function saveNewChild() {
    const name   = document.getElementById('nc-name').value.trim();
    const dob    = document.getElementById('nc-dob').value;
    const gender = document.getElementById('nc-gender').value;
    const blood  = document.getElementById('nc-blood').value;
    const weight = document.getElementById('nc-weight').value.trim();
    const height = document.getElementById('nc-height').value.trim();

    if (!name || !dob) {
        showToast('Full Name and Date of Birth are required.', 'error');
        return;
    }

    const btn = document.getElementById('save-add-child');
    btn.disabled    = true;
    btn.textContent = 'Adding…';

    try {
        const payload = {
            parent_id:     currentParentId,
            full_name:     name,
            date_of_birth: dob,
            gender:        gender || null,
            blood_type:    blood  || null,
            weight:        weight || null,
            height:        height || null,
            status:        'Active'
        };

        const { data: newChild, error } = await window.supabaseClient
            .from('CHILD')
            .insert([payload])
            .select('childid');

        if (error) throw error;

        // Also register child in CLINIC_PATIENT
        if (clinicId && newChild && newChild.length > 0) {
            await window.supabaseClient.from('CLINIC_PATIENT').insert([{
                clinicid: clinicId,
                userid:   currentParentId,
                childid:  newChild[0].childid
            }]).then(({ error: cpErr }) => {
                if (cpErr) console.warn('CLINIC_PATIENT child insert failed:', cpErr);
            });
        }

        showToast('Child added successfully!', 'success');
        closeAddChildModal();
        await loadChildren(currentParentId);

    } catch (err) {
        console.error('Error adding child:', err);
        showToast(err.message || 'Failed to add child.', 'error');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Add Child';
    }
}

// ─── MODAL WIRING ──────────────────────────────────────────────────────────
function wireModals() {
    // Breadcrumb back
    document.getElementById('breadcrumb-back').addEventListener('click', showListView);

    // Add Parent modal
    document.getElementById('btn-add-parent').addEventListener('click',    openAddParentModal);
    document.getElementById('close-add-parent').addEventListener('click',  closeAddParentModal);
    document.getElementById('cancel-add-parent').addEventListener('click', closeAddParentModal);
    document.getElementById('save-add-parent').addEventListener('click',   handleSaveParent);

    // Tab switching in Add Parent modal
    document.querySelectorAll('#add-parent-modal .p-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('#add-parent-modal .p-tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('#add-parent-modal .p-tab-panel').forEach(p => p.classList.remove('active'));
            tab.classList.add('active');
            document.getElementById(`panel-${tab.dataset.tab}`).classList.add('active');
        });
    });

    // Add Child modal
    document.getElementById('btn-add-child').addEventListener('click',    openAddChildModal);
    document.getElementById('close-add-child').addEventListener('click',  closeAddChildModal);
    document.getElementById('cancel-add-child').addEventListener('click', closeAddChildModal);
    document.getElementById('save-add-child').addEventListener('click',   saveNewChild);
}

// ─── HANDLE SAVE PARENT (dispatches based on active tab) ───────────────────
function handleSaveParent() {
    const activeTab = document.querySelector('#add-parent-modal .p-tab.active');
    if (activeTab && activeTab.dataset.tab === 'existing') {
        linkExistingParent();
    } else {
        saveNewParent();
    }
}

function openAddParentModal() {
    // Reset form fields
    ['np-name','np-email','np-phone','np-password'].forEach(id => {
        document.getElementById(id).value = '';
    });
    document.getElementById('ep-select').value = '';
    
    // Reset to "Create New" tab
    document.querySelectorAll('#add-parent-modal .p-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#add-parent-modal .p-tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelector('#add-parent-modal .p-tab[data-tab="new"]').classList.add('active');
    document.getElementById('panel-new').classList.add('active');

    // Reload existing parents dropdown
    loadExistingParentsForDropdown();

    document.getElementById('add-parent-modal').classList.add('open');
}

function closeAddParentModal() {
    document.getElementById('add-parent-modal').classList.remove('open');
}

function openAddChildModal() {
    ['nc-name','nc-dob','nc-weight','nc-height'].forEach(id => {
        document.getElementById(id).value = '';
    });
    document.getElementById('nc-gender').value = '';
    document.getElementById('nc-blood').value  = '';
    document.getElementById('add-child-modal').classList.add('open');
}

function closeAddChildModal() {
    document.getElementById('add-child-modal').classList.remove('open');
}

// ─── HELPERS ───────────────────────────────────────────────────────────────

// Calculate age string from ISO date e.g. "4y 5m"
function calcAge(dobStr) {
    const dob  = new Date(dobStr);
    const now  = new Date();
    let years  = now.getFullYear() - dob.getFullYear();
    let months = now.getMonth()    - dob.getMonth();
    if (months < 0) { years--; months += 12; }
    if (years < 0)  return '—';
    if (years === 0) return `${months}m`;
    return months > 0 ? `${years}y ${months}m` : `${years}y`;
}

// "15 Jan 2024"
function formatShortDate(isoStr) {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

// "15 January 2024"
function formatLongDate(isoStr) {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const months = ['January','February','March','April','May','June',
                    'July','August','September','October','November','December'];
    return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

// Toast notification
function showToast(msg, type = '') {
    const toast = document.getElementById('p-toast');
    if (!toast) return;
    toast.textContent      = msg;
    toast.style.background = type === 'error' ? '#ef4444' : '#10b981';
    toast.style.opacity    = '1';
    setTimeout(() => { toast.style.opacity = '0'; }, 3000);
}