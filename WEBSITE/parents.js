/* ============================================================
   parents.js — Carelyo Staff Portal
   Schema tables used:
     USER   (userid, full_name, email, phone_number, role, created_at, password)
     CHILD  (childid, parent_id FK→USER, full_name, date_of_birth, gender,
             blood_type, weight, height, status, created_at)
     CLINIC_PATIENT (clinic_patient_id, clinicid, userid, childid)
   ============================================================ */

let allParents          = [];
let clinicId            = null;
let currentParentId     = null;   // for the Add Child modal (from detail view)
let existingParentsData = [];     // for the "Link Existing" tab
let childRowCounter     = 0;      // unique ID counter for child rows
let pendingRemoveUser   = null;   // { userid, full_name } for the confirm modal

// ─── Init ──────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    const sessionData = localStorage.getItem('carelyo_admin_session');
    if (!sessionData) { window.location.href = 'login.html'; return; }
    clinicId = JSON.parse(sessionData).clinicid || null;

    wireModals();
    await loadParents();
    await loadExistingParentsForDropdown();

    // Live search
    const searchInput = document.getElementById('search-input');
    if (searchInput) {
        searchInput.addEventListener('input', e => {
            const q = e.target.value.toLowerCase();
            const filtered = allParents.filter(p =>
                (p.full_name || '').toLowerCase().includes(q) ||
                (p.email     || '').toLowerCase().includes(q)
            );
            renderParentsList(filtered);
        });
    }
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
async function loadParents() {
    const tbody = document.getElementById('parents-tbody');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:32px;color:#64748b;">Loading parents…</td></tr>`;

    try {
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

        if (clinicId) {
            const { data: cp } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .select('userid')
                .eq('clinicid', clinicId)
                .not('userid', 'is', null);

            let ids = cp ? [...new Set(cp.map(p => p.userid).filter(Boolean))] : [];

            // Fallback: infer from APPOINTMENT if CLINIC_PATIENT is empty
            if (ids.length === 0) {
                const { data: appts } = await window.supabaseClient
                    .from('APPOINTMENT')
                    .select('parentid')
                    .eq('clinicid', clinicId)
                    .not('parentid', 'is', null);
                if (appts) ids = [...new Set(appts.map(a => a.parentid).filter(Boolean))];
            }

            if (ids.length > 0) {
                query = query.in('userid', ids);
            } else {
                // No parents linked to this clinic — return empty
                allParents = [];
                renderParentsList(allParents);
                return;
            }
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
async function loadExistingParentsForDropdown() {
    const select = document.getElementById('ep-select');
    if (!select) return;

    const wrap = document.getElementById('ep-children-wrap');
    if (wrap) wrap.style.display = 'none';

    try {
        const { data: allParentUsers, error } = await window.supabaseClient
            .from('USER')
            .select('userid, full_name, email')
            .ilike('role', 'parent')
            .order('full_name', { ascending: true });

        if (error) throw error;

        let linkedUserIds = [];
        if (clinicId) {
            const { data: cp } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .select('userid')
                .eq('clinicid', clinicId)
                .not('userid', 'is', null);
            if (cp) linkedUserIds = [...new Set(cp.map(p => p.userid).filter(Boolean))];
        }

        const unlinkedParents = (allParentUsers || []).filter(
            p => !linkedUserIds.includes(p.userid)
        );

        if (unlinkedParents.length === 0) {
            select.innerHTML = '<option value="">No unlinked parents available</option>';
            existingParentsData = [];
            return;
        }

        const parentIds = unlinkedParents.map(p => p.userid);
        let childRows = [];
        if (parentIds.length > 0) {
            const { data, error: cErr } = await window.supabaseClient
                .from('CHILD')
                .select('childid, parent_id, full_name, date_of_birth, gender')
                .in('parent_id', parentIds);
            if (cErr) throw cErr;
            childRows = data || [];
        }

        const childrenByParent = {};
        childRows.forEach(c => {
            if (!childrenByParent[c.parent_id]) childrenByParent[c.parent_id] = [];
            childrenByParent[c.parent_id].push(c);
        });

        existingParentsData = unlinkedParents.map(p => ({
            ...p,
            CHILD: childrenByParent[p.userid] || []
        }));

        select.innerHTML = '<option value="">— Select a parent —</option>' +
            existingParentsData.map(p => {
                const childCount = p.CHILD.length;
                const label = childCount > 0
                    ? `${p.full_name} (${childCount} child${childCount > 1 ? 'ren' : ''})`
                    : `${p.full_name} (no children)`;
                return `<option value="${p.userid}">${label}</option>`;
            }).join('');

    } catch (err) {
        console.error('Error loading existing parents:', err);
        select.innerHTML = '<option value="">Error loading parents</option>';
    }
}

// ─── RENDER EXISTING PARENT'S CHILDREN ─────────────────────────────────────
function renderExistingChildren(parentId) {
    const wrap    = document.getElementById('ep-children-wrap');
    const list    = document.getElementById('ep-children-list');
    const countEl = document.getElementById('ep-children-count');
    if (!wrap || !list) return;

    const parent = existingParentsData.find(p => p.userid == parentId);
    if (!parent) {
        wrap.style.display = 'none';
        return;
    }

    const children = parent.CHILD || [];

    wrap.style.display = 'block';
    if (countEl) {
        countEl.textContent = children.length === 0
            ? 'no children'
            : `${children.length} child${children.length !== 1 ? 'ren' : ''}`;
    }

    if (children.length === 0) {
        list.innerHTML = `<div class="ep-children-empty">This parent has no children on record.</div>`;
        return;
    }

    list.innerHTML = children.map(c => {
        const initials = getInitials(c.full_name);
        const age      = c.date_of_birth ? calcAge(c.date_of_birth) : '—';
        const meta     = [age, c.gender].filter(Boolean).join(' • ');
        return `
            <div class="ep-child-item">
                <div class="ep-child-avatar">${initials}</div>
                <div class="ep-child-name">${c.full_name || '—'}</div>
                <div class="ep-child-meta">${meta}</div>
            </div>
        `;
    }).join('');
}

// ─── RENDER PARENTS TABLE ──────────────────────────────────────────────────
function renderParentsList(parents) {
    const tbody = document.getElementById('parents-tbody');
    if (!tbody) return;

    if (parents.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:32px;color:#64748b;">No parents found.</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    parents.forEach((parent, idx) => {
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
                <div class="row-actions">
                    <button class="view-children-btn" data-action="view">
                        View Children →
                    </button>
                    <button class="remove-btn" data-action="remove" title="Remove from this clinic">
                        <i class="ph ph-user-minus"></i> Remove
                    </button>
                </div>
            </td>
        `;

        tr.querySelector('[data-action="view"]').addEventListener('click', () => {
            openDetailView(parent);
        });
        tr.querySelector('[data-action="remove"]').addEventListener('click', () => {
            openRemoveModal(parent.userid, parent.full_name);
        });

        tbody.appendChild(tr);
    });
}

// ─── OPEN DETAIL VIEW ─────────────────────────────────────────────────────
async function openDetailView(parent) {
    currentParentId = parent.userid;

    document.getElementById('breadcrumb-name').textContent = parent.full_name || '—';
    document.getElementById('detail-name').textContent     = parent.full_name || '—';
    document.getElementById('detail-phone').textContent    = parent.phone_number || '—';
    document.getElementById('detail-email').textContent    = parent.email || '—';
    document.getElementById('detail-reg').textContent      =
        parent.created_at ? `Registered ${formatLongDate(parent.created_at)}` : '';

    // Wire the "Remove from Clinic" button for this specific parent
    const removeBtn = document.getElementById('btn-remove-parent-from-clinic');
    removeBtn.onclick = () => openRemoveModal(parent.userid, parent.full_name);

    showDetailView();
    await loadChildren(parent.userid);
}

// ─── LOAD CHILDREN FOR A PARENT ────────────────────────────────────────────
async function loadChildren(parentUserId) {
    const tbody = document.getElementById('children-tbody');
    if (!tbody) return;
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

// ─── CHILD ROW BUILDERS (for Add Parent modal) ─────────────────────────────
function buildChildRow() {
    const rowId = `np-child-${++childRowCounter}`;
    const div = document.createElement('div');
    div.className = 'np-child-row';
    div.id = rowId;
    div.innerHTML = `
        <div class="np-child-row-header">
            <span class="np-child-row-title">Child</span>
            <button type="button" class="np-child-remove" title="Remove child">
                <i class="ph ph-trash"></i>
            </button>
        </div>
        <div class="p-form-group" style="margin-bottom:8px;">
            <label class="p-form-label">Full Name *</label>
            <input type="text" class="p-form-control np-child-name" placeholder="Child's full name">
        </div>
        <div class="p-form-row">
            <div class="p-form-group">
                <label class="p-form-label">Date of Birth *</label>
                <input type="date" class="p-form-control np-child-dob">
            </div>
            <div class="p-form-group">
                <label class="p-form-label">Gender</label>
                <select class="p-form-control np-child-gender">
                    <option value="">—</option>
                    <option value="Male">Male</option>
                    <option value="Female">Female</option>
                </select>
            </div>
        </div>
        <div class="p-form-row">
            <div class="p-form-group">
                <label class="p-form-label">Blood Type</label>
                <select class="p-form-control np-child-blood">
                    <option value="">—</option>
                    <option value="A+">A+</option><option value="A-">A-</option>
                    <option value="B+">B+</option><option value="B-">B-</option>
                    <option value="O+">O+</option><option value="O-">O-</option>
                    <option value="AB+">AB+</option><option value="AB-">AB-</option>
                </select>
            </div>
            <div class="p-form-group">
                <label class="p-form-label">Weight (kg)</label>
                <input type="text" class="p-form-control np-child-weight" placeholder="e.g. 12.5">
            </div>
        </div>
        <div class="p-form-group" style="margin-bottom:0;">
            <label class="p-form-label">Height (cm)</label>
            <input type="text" class="p-form-control np-child-height" placeholder="e.g. 85">
        </div>
    `;
    div.querySelector('.np-child-remove').addEventListener('click', () => {
        div.remove();
        updateChildHint();
    });
    return div;
}

function addChildRow() {
    const container = document.getElementById('np-children-container');
    if (!container) return;
    container.appendChild(buildChildRow());
    updateChildHint();
}

function updateChildHint() {
    const container = document.getElementById('np-children-container');
    const hint = document.getElementById('np-child-hint');
    if (!container || !hint) return;
    const count = container.querySelectorAll('.np-child-row').length;
    hint.style.display = count === 0 ? 'block' : 'none';
}

// ─── ADD PARENT (Create New + Children) ────────────────────────────────────
async function saveNewParent() {
    const name     = document.getElementById('np-name').value.trim();
    const email    = document.getElementById('np-email').value.trim();
    const phone    = document.getElementById('np-phone').value.trim();
    const password = document.getElementById('np-password').value;

    if (!name || !email || !password) {
        showToast('Full Name, Email and Password are required.', 'error');
        return;
    }

    const childRows = [...document.querySelectorAll('#np-children-container .np-child-row')];
    const children = [];
    for (const row of childRows) {
        const cName = row.querySelector('.np-child-name').value.trim();
        const cDob  = row.querySelector('.np-child-dob').value;
        if (!cName || !cDob) {
            showToast('Each child needs a Full Name and Date of Birth.', 'error');
            return;
        }
        children.push({
            full_name:     cName,
            date_of_birth: cDob,
            gender:        row.querySelector('.np-child-gender').value || null,
            blood_type:    row.querySelector('.np-child-blood').value  || null,
            weight:        row.querySelector('.np-child-weight').value.trim() || null,
            height:        row.querySelector('.np-child-height').value.trim() || null,
        });
    }

    if (children.length === 0) {
        showToast('Please add at least one child.', 'error');
        return;
    }

    const btn = document.getElementById('save-add-parent');
    btn.disabled    = true;
    btn.textContent = 'Adding…';

    let newUserId = null;
    const createdChildIds = [];

    try {
        const { data: newUsers, error: userErr } = await window.supabaseClient
            .from('USER')
            .insert([{
                full_name:    name,
                email:        email,
                phone_number: phone || null,
                password:     password,
                role:         'parent'
            }])
            .select('userid');

        if (userErr) throw userErr;
        newUserId = newUsers[0].userid;

        const childPayloads = children.map(c => ({
            parent_id:     newUserId,
            full_name:     c.full_name,
            date_of_birth: c.date_of_birth,
            gender:        c.gender,
            blood_type:    c.blood_type,
            weight:        c.weight,
            height:        c.height,
            status:        'Active'
        }));

        const { data: newChildren, error: childErr } = await window.supabaseClient
            .from('CHILD')
            .insert(childPayloads)
            .select('childid');

        if (childErr) {
            console.warn('Child insert failed, rolling back parent:', childErr);
            await window.supabaseClient.from('USER').delete().eq('userid', newUserId);
            throw new Error('Failed to add children. Parent was not created.');
        }

        (newChildren || []).forEach(c => createdChildIds.push(c.childid));

        if (clinicId) {
            const cpRows = [
                { clinicid: clinicId, userid: newUserId, childid: null },
                ...createdChildIds.map(cid => ({
                    clinicid: clinicId,
                    userid:   newUserId,
                    childid:  cid
                }))
            ];

            const { error: cpErr } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .insert(cpRows);

            if (cpErr) {
                console.warn('CLINIC_PATIENT insert failed:', cpErr);
                showToast('Parent and children created, but clinic linking failed. Please link manually.', 'error');
            } else {
                showToast(
                    `Parent and ${children.length} child${children.length > 1 ? 'ren' : ''} added successfully!`,
                    'success'
                );
            }
        } else {
            showToast(
                `Parent and ${children.length} child${children.length > 1 ? 'ren' : ''} added successfully!`,
                'success'
            );
        }

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

// ─── LINK EXISTING PARENT + THEIR CHILDREN ─────────────────────────────────
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
        const uid = parseInt(selectedUserId, 10);

        const { data: parentCheck, error: pErr } = await window.supabaseClient
            .from('USER')
            .select('userid, role')
            .eq('userid', uid)
            .maybeSingle();

        if (pErr) throw pErr;
        if (!parentCheck) {
            showToast('Parent not found in database.', 'error');
            return;
        }

        const { data: realChildren, error: cErr } = await window.supabaseClient
            .from('CHILD')
            .select('childid, full_name')
            .eq('parent_id', uid);

        if (cErr) throw cErr;
        const childIds = (realChildren || []).map(c => c.childid);

        const { data: existing } = await window.supabaseClient
            .from('CLINIC_PATIENT')
            .select('userid, childid')
            .eq('clinicid', clinicId)
            .eq('userid', uid);

        const linkedChildIds = new Set(
            (existing || []).map(r => r.childid).filter(Boolean)
        );
        const parentAlreadyLinked = (existing || []).some(r => r.childid === null);

        const rowsToInsert = [];

        if (!parentAlreadyLinked) {
            rowsToInsert.push({ clinicid: clinicId, userid: uid, childid: null });
        }

        childIds.forEach(cid => {
            if (!linkedChildIds.has(cid)) {
                rowsToInsert.push({ clinicid: clinicId, userid: uid, childid: cid });
            }
        });

        if (rowsToInsert.length === 0) {
            showToast('This parent and all their existing children are already linked.', 'error');
            return;
        }

        const { error: insErr } = await window.supabaseClient
            .from('CLINIC_PATIENT')
            .insert(rowsToInsert);

        if (insErr) throw insErr;

        const newChildCount = rowsToInsert.filter(r => r.childid !== null).length;
        const parentMsg = parentAlreadyLinked ? '' : 'Parent';
        const childMsg = newChildCount > 0
            ? `${parentMsg ? ' and ' : ''}${newChildCount} child${newChildCount > 1 ? 'ren' : ''}`
            : '';
        showToast(`${parentMsg}${childMsg} linked successfully!`, 'success');

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

// ─── REMOVE PARENT FROM CLINIC ─────────────────────────────────────────────
// Only deletes CLINIC_PATIENT rows for this clinic + this parent (and their
// children). Does NOT touch USER or CHILD rows.
function openRemoveModal(userid, fullName) {
    pendingRemoveUser = { userid, full_name: fullName || 'this parent' };
    document.getElementById('remove-parent-name').textContent = pendingRemoveUser.full_name;
    document.getElementById('remove-parent-modal').classList.add('open');
}

function closeRemoveModal() {
    document.getElementById('remove-parent-modal').classList.remove('open');
    pendingRemoveUser = null;
}

async function confirmRemoveParent() {
    if (!pendingRemoveUser) return;

    if (!clinicId) {
        showToast('Clinic ID not found. Please re-login.', 'error');
        return;
    }

    const btn = document.getElementById('confirm-remove-parent');
    btn.disabled = true;
    const originalHtml = btn.innerHTML;
    btn.innerHTML = '<i class="ph ph-circle-notch"></i> Removing…';

    try {
        const uid = pendingRemoveUser.userid;

        // Delete all CLINIC_PATIENT rows for this clinic + this user
        // (covers both the parent row and any child rows pointing to them)
        const { error } = await window.supabaseClient
            .from('CLINIC_PATIENT')
            .delete()
            .eq('clinicid', clinicId)
            .eq('userid', uid);

        if (error) throw error;

        const removedName = pendingRemoveUser.full_name;
        closeRemoveModal();

        // If we were viewing this parent's detail, go back to the list
        if (currentParentId === uid) {
            currentParentId = null;
            showListView();
        }

        showToast(`${removedName} removed from this clinic.`, 'success');

        // Refresh both the list and the dropdown so they can be re-linked
        await loadParents();
        await loadExistingParentsForDropdown();

    } catch (err) {
        console.error('Error removing parent from clinic:', err);
        showToast(err.message || 'Failed to remove parent from clinic.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalHtml;
    }
}

// ─── ADD CHILD (from detail view) ──────────────────────────────────────────
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

        if (clinicId && newChild && newChild.length > 0) {
            const { error: cpErr } = await window.supabaseClient
                .from('CLINIC_PATIENT')
                .insert([{
                    clinicid: clinicId,
                    userid:   currentParentId,
                    childid:  newChild[0].childid
                }]);

            if (cpErr) {
                console.warn('CLINIC_PATIENT child insert failed:', cpErr);
                showToast('Child created, but clinic linking failed.', 'error');
            } else {
                showToast('Child added successfully!', 'success');
            }
        } else {
            showToast('Child added successfully!', 'success');
        }

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

    // Add Child row button inside Add Parent modal
    document.getElementById('btn-add-child-row').addEventListener('click', addChildRow);

    // Tab switching in Add Parent modal
    document.querySelectorAll('#add-parent-modal .p-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('#add-parent-modal .p-tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('#add-parent-modal .p-tab-panel').forEach(p => p.classList.remove('active'));
            tab.classList.add('active');
            document.getElementById(`panel-${tab.dataset.tab}`).classList.add('active');
        });
    });

    // Add Child modal (from detail view)
    document.getElementById('btn-add-child').addEventListener('click',    openAddChildModal);
    document.getElementById('close-add-child').addEventListener('click',  closeAddChildModal);
    document.getElementById('cancel-add-child').addEventListener('click', closeAddChildModal);
    document.getElementById('save-add-child').addEventListener('click',   saveNewChild);

    // Existing parent dropdown change → render their children
    document.getElementById('ep-select').addEventListener('change', (e) => {
        renderExistingChildren(e.target.value);
    });

    // Remove parent modal
    document.getElementById('close-remove-parent').addEventListener('click',  closeRemoveModal);
    document.getElementById('cancel-remove-parent').addEventListener('click', closeRemoveModal);
    document.getElementById('confirm-remove-parent').addEventListener('click', confirmRemoveParent);
    // Click on backdrop to close
    document.getElementById('remove-parent-modal').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) closeRemoveModal();
    });
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
    ['np-name','np-email','np-phone','np-password'].forEach(id => {
        document.getElementById(id).value = '';
    });
    document.getElementById('ep-select').value = '';

    const container = document.getElementById('np-children-container');
    container.innerHTML = '';
    childRowCounter = 0;
    addChildRow();

    document.querySelectorAll('#add-parent-modal .p-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#add-parent-modal .p-tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelector('#add-parent-modal .p-tab[data-tab="new"]').classList.add('active');
    document.getElementById('panel-new').classList.add('active');

    loadExistingParentsForDropdown();

    const epWrap = document.getElementById('ep-children-wrap');
    if (epWrap) epWrap.style.display = 'none';

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
function getInitials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
}

function calcAge(dobStr) {
    if (!dobStr) return '—';
    const dob  = new Date(dobStr);
    const now  = new Date();
    let years  = now.getFullYear() - dob.getFullYear();
    let months = now.getMonth()    - dob.getMonth();
    if (months < 0) { years--; months += 12; }
    if (years < 0)  return '—';
    if (years === 0) return `${months}m`;
    return months > 0 ? `${years}y ${months}m` : `${years}y`;
}

function formatShortDate(isoStr) {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

function formatLongDate(isoStr) {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const months = ['January','February','March','April','May','June',
                    'July','August','September','October','November','December'];
    return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

function showToast(msg, type = '') {
    const toast = document.getElementById('p-toast');
    if (!toast) return;
    toast.textContent      = msg;
    toast.style.background = type === 'error' ? '#ef4444' : '#10b981';
    toast.style.opacity    = '1';
    setTimeout(() => { toast.style.opacity = '0'; }, 3000);
}