/**
 * vaccinations.js  –  Vaccination Status page for Carelyo staff portal
 *
 * Data model:
 *  VACCINATION      – master list of all vaccines (vaccineid, vaccine_name, recommended_age_weeks)
 *  CHILD_VACCINE    – per-child vaccine records (childid, vaccineid, status, administered_date,
 *                     administered_at, notes, childvaccineid)
 *  CHILD            – children (childid, full_name, date_of_birth, parent_id)
 *  USER             – users / parents (userid, full_name)
 *
 * Statuses stored in CHILD_VACCINE.status (case-insensitive match):
 *   completed | scheduled | due | overdue | skipped
 */

document.addEventListener('DOMContentLoaded', () => {

    // ── State ────────────────────────────────────────────────────────────────
    let allVaccines   = [];   // master vaccine list, sorted by recommended_age_weeks
    let allChildren   = [];   // enriched child records (with childVaccineMap)
    let filteredRows  = [];   // currently displayed rows (after search/filter)
    let adminLog      = [];   // administration log entries
    let activeFilter  = 'all';
    let editCtx       = null; // { childvaccineid, childid, vaccineid, childName, vaccineName }

    // ── Boot ─────────────────────────────────────────────────────────────────
    loadData();

    // ── Filter tabs ──────────────────────────────────────────────────────────
    document.querySelectorAll('.filter-tab').forEach(tab => {
        tab.addEventListener('click', e => {
            document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
            e.currentTarget.classList.add('active');
            activeFilter = e.currentTarget.dataset.filter;
            applyFilters();
        });
    });

    // ── Search ───────────────────────────────────────────────────────────────
    document.getElementById('search-input').addEventListener('input', applyFilters);

    // ── Modal close ──────────────────────────────────────────────────────────
    document.getElementById('modal-cancel').addEventListener('click', closeModal);
    document.getElementById('edit-modal').addEventListener('click', e => {
        if (e.target === e.currentTarget) closeModal();
    });
    document.getElementById('modal-save').addEventListener('click', saveStatus);

    // ═════════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═════════════════════════════════════════════════════════════════════════

    async function loadData() {
        try {
            let session  = JSON.parse(localStorage.getItem('carelyo_admin_session') || '{}');
            let clinicId = session.clinicid || (session.clinic ? session.clinic.clinicid : null);

            // Resolve clinicId from CLINIC_STAFF if not yet cached in session
            if (!clinicId && session.userid && window.supabaseClient) {
                try {
                    const { data: staffData } = await window.supabaseClient
                        .from('CLINIC_STAFF')
                        .select('*')
                        .eq('userid', session.userid)
                        .maybeSingle();

                    if (staffData && staffData.clinicid) {
                        clinicId = staffData.clinicid;
                        session.clinicid = clinicId;
                        localStorage.setItem('carelyo_admin_session', JSON.stringify(session));
                    }
                } catch (e) {
                    console.warn('Could not resolve clinicId from CLINIC_STAFF:', e);
                }
            }

            // 1. Master vaccine list
            const { data: vaccines, error: vErr } = await window.supabaseClient
                .from('VACCINATION')
                .select('vaccineid, vaccine_name, recommended_age_weeks')
                .order('recommended_age_weeks', { ascending: true });

            if (vErr) throw vErr;
            allVaccines = vaccines || [];

            // 2. Children registered at this clinic (with parent name)
            let childQuery = window.supabaseClient
                .from('CHILD')
                .select(`
                    childid, full_name, date_of_birth,
                    USER!inner(full_name),
                    CHILD_VACCINE(childvaccineid, vaccineid, status, administered_date, administered_at, notes)
                `)
                .order('full_name', { ascending: true });

            // If clinic-scoped, filter by clinic's children
            if (clinicId) {
                // Primary: use CLINIC_PATIENT
                const { data: cpRows } = await window.supabaseClient
                    .from('CLINIC_PATIENT')
                    .select('childid')
                    .eq('clinicid', clinicId);

                if (cpRows && cpRows.length > 0) {
                    const ids = cpRows.map(r => r.childid);
                    childQuery = childQuery.in('childid', ids);
                } else {
                    // Fallback: derive child IDs from APPOINTMENT if CLINIC_PATIENT has no rows yet
                    const { data: apptRows } = await window.supabaseClient
                        .from('APPOINTMENT')
                        .select('childid')
                        .eq('clinicid', clinicId);

                    if (apptRows && apptRows.length > 0) {
                        const ids = [...new Set(apptRows.map(r => r.childid).filter(Boolean))];
                        childQuery = childQuery.in('childid', ids);
                    } else {
                        // No children found for this clinic at all — return empty
                        childQuery = childQuery.in('childid', [-1]);
                    }
                }
            }

            const { data: children, error: cErr } = await childQuery;
            if (cErr) throw cErr;

            // 3. Enrich children
            allChildren = (children || []).map(child => {
                // Build a map: vaccineid → child_vaccine record
                const cvMap = {};
                (child.CHILD_VACCINE || []).forEach(cv => {
                    cvMap[cv.vaccineid] = cv;
                });

                const parentName = child.USER ? child.USER.full_name : 'Unknown';
                return { ...child, cvMap, parentName };
            });

            // 4. Build admin log  (completed entries with a date)
            adminLog = [];
            allChildren.forEach(child => {
                Object.values(child.cvMap).forEach(cv => {
                    if ((cv.status || '').toLowerCase() === 'completed' && cv.administered_date) {
                        const vac = allVaccines.find(v => v.vaccineid === cv.vaccineid);
                        adminLog.push({
                            childName    : child.full_name,
                            vaccineName  : vac ? vac.vaccine_name : `#${cv.vaccineid}`,
                            date         : cv.administered_date,
                            time         : cv.administered_at
                                ? new Date(cv.administered_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                                : '—',
                            administeredBy : cv.notes || '—',   // notes used as "administered by" if available
                            batchNo       : '—',
                        });
                    }
                });
            });
            adminLog.sort((a, b) => new Date(b.date) - new Date(a.date));

            // 5. Render
            buildTableHeader();
            applyFilters();
            renderAdminLog();

        } catch (err) {
            console.error('Vaccination load error:', err);
            document.getElementById('vac-tbody').innerHTML =
                `<tr><td colspan="20" style="text-align:center;padding:32px;color:#ef4444;">
                    Failed to load data: ${err.message || err}
                </td></tr>`;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TABLE HEADER
    // ═════════════════════════════════════════════════════════════════════════

    function buildTableHeader() {
        const nameRow = document.getElementById('vac-thead-name');
        const ageRow  = document.getElementById('vac-thead-age');

        // Child column
        let nameHtml = '<th class="child-col">Child</th>';
        let ageHtml  = '<th class="child-col"></th>';

        allVaccines.forEach(v => {
            nameHtml += `<th>${escHtml(v.vaccine_name)}</th>`;
            ageHtml  += `<th>${formatAge(v.recommended_age_weeks)}</th>`;
        });

        // Progress column
        nameHtml += '<th class="progress-col">Progress</th>';
        ageHtml  += '<th class="progress-col"></th>';

        nameRow.innerHTML = nameHtml;
        ageRow.innerHTML  = ageHtml;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FILTERING & SEARCH
    // ═════════════════════════════════════════════════════════════════════════

    function applyFilters() {
        const searchQ = (document.getElementById('search-input').value || '').toLowerCase().trim();

        filteredRows = allChildren.filter(child => {
            // Search
            if (searchQ) {
                const cn = (child.full_name   || '').toLowerCase();
                const pn = (child.parentName  || '').toLowerCase();
                if (!cn.includes(searchQ) && !pn.includes(searchQ)) return false;
            }

            // Status filter (based on any vaccine in that child having that status)
            if (activeFilter !== 'all') {
                const statuses = Object.values(child.cvMap).map(cv => (cv.status || '').toLowerCase());
                if (activeFilter === 'completed') {
                    // All vaccines completed
                    const completedCount = statuses.filter(s => s === 'completed').length;
                    return completedCount === allVaccines.length;
                }
                return statuses.some(s => s === activeFilter);
            }
            return true;
        });

        updateSummaryCards();
        renderTableBody(filteredRows);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SUMMARY CARDS
    // ═════════════════════════════════════════════════════════════════════════

    function updateSummaryCards() {
        let fullyCompleted = 0, dueNow = 0, overdue = 0, skipped = 0;

        allChildren.forEach(child => {
            const statuses = Object.values(child.cvMap).map(cv => (cv.status || '').toLowerCase());
            const completedCount = statuses.filter(s => s === 'completed').length;

            if (completedCount === allVaccines.length && allVaccines.length > 0) fullyCompleted++;
            if (statuses.some(s => s === 'due'))      dueNow++;
            if (statuses.some(s => s === 'overdue'))  overdue++;
            if (statuses.some(s => s === 'skipped'))  skipped++;
        });

        document.getElementById('vac-fully-completed').textContent = fullyCompleted;
        document.getElementById('vac-due-now').textContent         = dueNow;
        document.getElementById('vac-overdue').textContent         = overdue;
        document.getElementById('vac-skipped').textContent         = skipped;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TABLE BODY
    // ═════════════════════════════════════════════════════════════════════════

    function renderTableBody(rows) {
        const tbody = document.getElementById('vac-tbody');

        if (rows.length === 0) {
            tbody.innerHTML = `<tr><td colspan="${allVaccines.length + 2}" style="text-align:center;padding:32px;color:#9ca3af;">No records found.</td></tr>`;
            return;
        }

        tbody.innerHTML = rows.map(child => {
            let completedCount = 0;
            let hasDue = false, hasOverdue = false;

            const dotCells = allVaccines.map(v => {
                const cv = child.cvMap[v.vaccineid];
                if (!cv) {
                    // No record yet – show as scheduled/empty
                    return `<td><span class="cell-dot scheduled"
                        data-childvaccineid=""
                        data-childid="${child.childid}"
                        data-vaccineid="${v.vaccineid}"
                        data-childname="${escAttr(child.full_name)}"
                        data-vaccinename="${escAttr(v.vaccine_name)}"
                        data-status="scheduled"
                        title="${escAttr(v.vaccine_name)} – Not recorded"
                        ></span></td>`;
                }

                const status = (cv.status || 'scheduled').toLowerCase();
                if (status === 'completed') completedCount++;
                if (status === 'due')      hasDue = true;
                if (status === 'overdue')  hasOverdue = true;

                const dateStr = cv.administered_date || '';
                const tipDate = dateStr ? `\nDate: ${dateStr}` : '';
                const tip     = `${escAttr(v.vaccine_name)} – ${capitalize(status)}${tipDate}`;

                return `<td><span class="cell-dot ${status}"
                    data-childvaccineid="${cv.childvaccineid}"
                    data-childid="${child.childid}"
                    data-vaccineid="${v.vaccineid}"
                    data-childname="${escAttr(child.full_name)}"
                    data-vaccinename="${escAttr(v.vaccine_name)}"
                    data-status="${status}"
                    data-date="${dateStr}"
                    data-notes="${escAttr(cv.notes || '')}"
                    title="${tip}"
                    ></span></td>`;
            }).join('');

            const total   = allVaccines.length;
            const pct     = total > 0 ? Math.round((completedCount / total) * 100) : 0;
            const barColor = hasOverdue ? 'red' : (hasDue ? 'orange' : 'green');

            const progressHtml = `
                <div class="progress-bar-wrap">
                    <div class="progress-bar-track">
                        <div class="progress-bar-fill ${barColor}" style="width:${pct}%"></div>
                    </div>
                    <span class="progress-pct">${pct}%</span>
                </div>`;

            return `<tr>
                <td class="child-name">${escHtml(child.full_name)}</td>
                ${dotCells}
                <td class="progress-col">${progressHtml}</td>
            </tr>`;
        }).join('');

        // Attach click handlers to all dots
        tbody.querySelectorAll('.cell-dot').forEach(dot => {
            dot.addEventListener('click', () => openModal(dot));
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ADMINISTRATION LOG
    // ═════════════════════════════════════════════════════════════════════════

    function renderAdminLog() {
        const tbody = document.getElementById('admin-log-tbody');

        if (adminLog.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:24px;color:#9ca3af;">No administration records found.</td></tr>`;
            return;
        }

        tbody.innerHTML = adminLog.map(entry => `
            <tr>
                <td>${escHtml(entry.childName)}</td>
                <td>${escHtml(entry.vaccineName)}</td>
                <td class="muted">${escHtml(entry.date)}</td>
                <td class="muted">${escHtml(entry.time)}</td>
                <td>${escHtml(entry.administeredBy)}</td>
                <td class="muted">${escHtml(entry.batchNo)}</td>
            </tr>`).join('');
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EDIT MODAL
    // ═════════════════════════════════════════════════════════════════════════

    function openModal(dot) {
        editCtx = {
            childvaccineid : dot.dataset.childvaccineid || null,
            childid        : parseInt(dot.dataset.childid),
            vaccineid      : parseInt(dot.dataset.vaccineid),
            childName      : dot.dataset.childname,
            vaccineName    : dot.dataset.vaccinename,
        };

        document.getElementById('modal-child-name').value   = editCtx.childName;
        document.getElementById('modal-vaccine-name').value = editCtx.vaccineName;
        document.getElementById('modal-status').value       = dot.dataset.status || 'scheduled';
        document.getElementById('modal-date').value         = dot.dataset.date   || '';
        document.getElementById('modal-notes').value        = dot.dataset.notes  || '';

        document.getElementById('edit-modal').classList.add('open');
    }

    function closeModal() {
        document.getElementById('edit-modal').classList.remove('open');
        editCtx = null;
    }

    async function saveStatus() {
        if (!editCtx) return;

        const status = document.getElementById('modal-status').value;
        const date   = document.getElementById('modal-date').value   || null;
        const notes  = document.getElementById('modal-notes').value  || null;

        const saveBtn = document.getElementById('modal-save');
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving…';

        try {
            const payload = {
                childid            : editCtx.childid,
                vaccineid          : editCtx.vaccineid,
                status             : status,
                administered_date  : (status === 'completed' && date) ? date : null,
                administered_at    : (status === 'completed' && date) ? new Date(date).toISOString() : null,
                notes              : notes,
            };

            let error;
            if (editCtx.childvaccineid) {
                // Update existing record
                ({ error } = await window.supabaseClient
                    .from('CHILD_VACCINE')
                    .update(payload)
                    .eq('childvaccineid', editCtx.childvaccineid));
            } else {
                // Insert new record
                ({ error } = await window.supabaseClient
                    .from('CHILD_VACCINE')
                    .insert(payload));
            }

            if (error) throw error;

            closeModal();
            await loadData(); // Refresh everything

        } catch (err) {
            console.error('Save error:', err);
            alert('Failed to save: ' + (err.message || err));
        } finally {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save';
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    function formatAge(weeks) {
        if (weeks === null || weeks === undefined) return '';
        if (weeks === 0) return 'Birth';
        if (weeks < 8)  return `${weeks} wk`;
        const months = Math.round(weeks / 4.33);
        if (months < 12) return `${months} mo`;
        const years = Math.round(weeks / 52);
        return `${years} yr`;
    }

    function capitalize(str) {
        if (!str) return '';
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    function escHtml(str) {
        return String(str ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function escAttr(str) {
        return String(str ?? '').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
});
