/* ============================================================
   health.js — Child Records Page
   Receives ?childid=X from the URL (set by parents.js View Records btn)

   Tables queried:
     CHILD          (childid, parent_id, full_name, date_of_birth, gender,
                     blood_type, weight, height, status)
     USER           (userid, full_name)          — parent info via CHILD.parent_id
     ALLERGIES      (allergieid, allergy_type, allergy_name, severity, notes)
     MEDICAL_HISTORY(medicalhisid, condition_name, diagnosis_date, treatment, notes)
     MEDICATION     (medid, medication_name, dosage, frequency, start_date,
                     end_date, notes, is_active)
     DOCTOR_VISIT   (docvisitid, visit_date, raw_notes, summary, userid→staff)
   ============================================================ */

document.addEventListener('DOMContentLoaded', async () => {
    // ── 1. Read childid from URL ──────────────────────────────────────────
    const params  = new URLSearchParams(window.location.search);
    const childId = parseInt(params.get('childid'), 10);

    if (!childId) {
        document.getElementById('main-content').innerHTML =
            `<div style="padding:48px;text-align:center;color:#ef4444;">
                No child selected. <a href="parents.html" style="color:var(--primary-color);">Go back to Parents</a>
             </div>`;
        return;
    }

    // ── 2. Fetch all data in parallel ────────────────────────────────────
    try {
        const [
            childRes,
            allergyRes,
            historyRes,
            medRes,
            visitRes
        ] = await Promise.all([
            // CHILD + parent name via parent_id FK → USER
            window.supabaseClient
                .from('CHILD')
                .select('*, USER:parent_id ( userid, full_name, phone_number )')
                .eq('childid', childId)
                .single(),

            // ALLERGIES for this child
            window.supabaseClient
                .from('ALLERGIES')
                .select('allergieid, allergy_type, allergy_name, severity, notes')
                .eq('childid', childId),

            // MEDICAL_HISTORY
            window.supabaseClient
                .from('MEDICAL_HISTORY')
                .select('medicalhisid, condition_name, diagnosis_date, treatment, notes')
                .eq('childid', childId)
                .order('diagnosis_date', { ascending: false }),

            // MEDICATION (active and inactive)
            window.supabaseClient
                .from('MEDICATION')
                .select('medid, medication_name, dosage, frequency, start_date, end_date, notes, is_active')
                .eq('childid', childId)
                .order('is_active', { ascending: false })
                .order('created_at', { ascending: false }),

            // DOCTOR_VISIT + staff name via userid FK → USER
            window.supabaseClient
                .from('DOCTOR_VISIT')
                .select('docvisitid, visit_date, raw_notes, summary, USER:userid ( full_name )')
                .eq('childid', childId)
                .order('visit_date', { ascending: false })
        ]);

        if (childRes.error) throw childRes.error;

        const child    = childRes.data;
        const allergies= allergyRes.data  || [];
        const history  = historyRes.data  || [];
        const meds     = medRes.data      || [];
        const visits   = visitRes.data    || [];

        // ── 3. Render the page ────────────────────────────────────────────
        renderPage(child, allergies, history, meds, visits);

    } catch (err) {
        console.error('Error loading child records:', err);
        document.getElementById('main-content').innerHTML =
            `<div style="padding:48px;text-align:center;color:#ef4444;">Failed to load records.</div>`;
    }
});

// ─── Main render ───────────────────────────────────────────────────────────
function renderPage(child, allergies, history, meds, visits) {
    const parent     = child.USER;
    const parentName = parent ? parent.full_name : 'Unknown';
    const childName  = child.full_name || '—';
    const initials   = getInitials(childName);

    // Counts for tab badges
    const allergyCount  = allergies.length;
    const historyCount  = history.length;
    const medCount      = meds.length;
    const visitCount    = visits.length;

    const main = document.getElementById('main-content');
    main.innerHTML = `
        <!-- Breadcrumb -->
        <div class="hr-breadcrumb">
            <button class="hr-bc-link" onclick="window.location.href='parents.html'">Parents</button>
            <span class="hr-bc-sep">›</span>
            <button class="hr-bc-link" onclick="history.back()">${parentName}</button>
            <span class="hr-bc-sep">›</span>
            <span class="hr-bc-cur">${childName}</span>
        </div>

        <!-- Child info card -->
        <div class="child-info-card">
            <div class="child-info-left">
                <div class="child-avatar">${initials}</div>
                <div>
                    <div class="child-info-name">${childName}</div>
                    <div class="child-info-meta">
                        <span class="child-meta-item">DOB: ${child.date_of_birth || '—'}</span>
                        <span class="child-meta-divider"></span>
                        <span class="child-meta-item">Age: ${calcAge(child.date_of_birth)}</span>
                        <span class="child-meta-divider"></span>
                        <span class="child-meta-item">Gender: ${child.gender || '—'}</span>
                        ${child.blood_type ? `<span class="child-meta-divider"></span><span class="child-meta-item"><i class="ph ph-drop" style="color:#ef4444;font-size:13px;"></i>${child.blood_type}</span>` : ''}
                        ${child.weight ? `<span class="child-meta-divider"></span><span class="child-meta-item">${child.weight} kg</span>` : ''}
                        ${child.height ? `<span class="child-meta-divider"></span><span class="child-meta-item">${child.height} cm</span>` : ''}
                    </div>
                </div>
            </div>
            <span class="child-status-badge">${child.status || 'Active'}</span>
        </div>

        <!-- Tabs -->
        <div class="hr-tabs">
            <button class="hr-tab active" data-tab="overview">Overview</button>
            <button class="hr-tab" data-tab="allergies">Allergies${allergyCount > 0 ? ` (${allergyCount})` : ''}</button>
            <button class="hr-tab" data-tab="history">Medical History${historyCount > 0 ? ` (${historyCount})` : ''}</button>
            <button class="hr-tab" data-tab="medications">Medications${medCount > 0 ? ` (${medCount})` : ' (0)'}</button>
            <button class="hr-tab" data-tab="visits">Visit Notes${visitCount > 0 ? ` (${visitCount})` : ''}</button>
        </div>

        <!-- Tab panels -->
        <div id="panel-overview"   class="hr-panel active">${buildOverviewPanel(allergies, history, meds)}</div>
        <div id="panel-allergies"  class="hr-panel">${buildAllergiesPanel(allergies)}</div>
        <div id="panel-history"    class="hr-panel">${buildHistoryPanel(history)}</div>
        <div id="panel-medications"class="hr-panel">${buildMedicationsPanel(meds)}</div>
        <div id="panel-visits"     class="hr-panel">${buildVisitsPanel(visits)}</div>
    `;

    // Wire tabs
    main.querySelectorAll('.hr-tab').forEach(btn => {
        btn.addEventListener('click', () => {
            main.querySelectorAll('.hr-tab').forEach(t => t.classList.remove('active'));
            main.querySelectorAll('.hr-panel').forEach(p => p.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById(`panel-${btn.dataset.tab}`).classList.add('active');
        });
    });
}

// ─── Overview panel ────────────────────────────────────────────────────────
// 3 summary cards: Allergies | Active Conditions | Active Medications
function buildOverviewPanel(allergies, history, meds) {
    // Allergies card
    const allergyItems = allergies.length > 0
        ? allergies.map(a => `<div class="overview-item">• ${a.allergy_name}${a.severity ? ` (${a.severity})` : ''}</div>`).join('')
        : `<div class="overview-none">None on record</div>`;

    // Active conditions (MEDICAL_HISTORY.condition_name)
    const condItems = history.length > 0
        ? history.map(h => `<div class="overview-item">• ${h.condition_name || '—'}</div>`).join('')
        : `<div class="overview-none">None on record</div>`;

    // Active medications (MEDICATION where is_active = true)
    const activeMeds = meds.filter(m => m.is_active);
    const medItems   = activeMeds.length > 0
        ? activeMeds.map(m => `<div class="overview-item">• ${m.medication_name || '—'}${m.dosage ? ` — ${m.dosage}` : ''}</div>`).join('')
        : `<div class="overview-none">None on record</div>`;

    return `
        <div class="overview-grid">
            <div class="overview-card">
                <div class="overview-card-title">
                    <i class="ph ph-warning-triangle ov-icon-red"></i> Allergies
                </div>
                ${allergyItems}
            </div>
            <div class="overview-card">
                <div class="overview-card-title">
                    <i class="ph ph-file-text ov-icon-blue"></i> Active Conditions
                </div>
                ${condItems}
            </div>
            <div class="overview-card">
                <div class="overview-card-title">
                    <i class="ph ph-pill ov-icon-purple"></i> Active Medications
                </div>
                ${medItems}
            </div>
        </div>`;
}

// ─── Allergies panel ───────────────────────────────────────────────────────
// Columns: Allergy Type | Allergy Name | Severity | Notes
function buildAllergiesPanel(allergies) {
    if (allergies.length === 0) return emptyState('No allergies on record.');

    const rows = allergies.map(a => {
        const sev      = (a.severity || '').toLowerCase();
        const sevClass = sev === 'severe' ? 'sev-severe' : sev === 'moderate' ? 'sev-moderate' : 'sev-mild';
        const sevBadge = a.severity
            ? `<span class="severity-pill ${sevClass}">${a.severity}</span>`
            : '—';
        return `<tr>
            <td>${a.allergy_type || '—'}</td>
            <td style="font-weight:600;">${a.allergy_name || '—'}</td>
            <td>${sevBadge}</td>
            <td style="color:#64748b;font-size:13px;">${a.notes || '—'}</td>
        </tr>`;
    }).join('');

    return `<div class="hr-section-card">
        <table class="hr-table">
            <thead><tr><th>Type</th><th>Allergy</th><th>Severity</th><th>Notes</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
    </div>`;
}

// ─── Medical History panel ─────────────────────────────────────────────────
// Columns: Condition | Diagnosed | Treatment | Notes
function buildHistoryPanel(history) {
    if (history.length === 0) return emptyState('No medical history on record.');

    const rows = history.map(h => `<tr>
        <td style="font-weight:600;">${h.condition_name || '—'}</td>
        <td style="color:#64748b;font-size:13px;">${h.diagnosis_date || '—'}</td>
        <td>${h.treatment || '—'}</td>
        <td style="color:#64748b;font-size:13px;">${h.notes || '—'}</td>
    </tr>`).join('');

    return `<div class="hr-section-card">
        <table class="hr-table">
            <thead><tr><th>Condition</th><th>Diagnosed</th><th>Treatment</th><th>Notes</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
    </div>`;
}

// ─── Medications panel ─────────────────────────────────────────────────────
// Columns: Medication | Dosage | Frequency | Start | End | Status | Notes
function buildMedicationsPanel(meds) {
    if (meds.length === 0) return emptyState('No medications on record.');

    const rows = meds.map(m => {
        const badge = m.is_active
            ? `<span class="med-active-badge">Active</span>`
            : `<span class="med-inactive-badge">Inactive</span>`;
        return `<tr>
            <td style="font-weight:600;">${m.medication_name || '—'}</td>
            <td>${m.dosage || '—'}</td>
            <td>${m.frequency || '—'}</td>
            <td style="color:#64748b;font-size:13px;">${m.start_date || '—'}</td>
            <td style="color:#64748b;font-size:13px;">${m.end_date || '—'}</td>
            <td>${badge}</td>
            <td style="color:#64748b;font-size:13px;">${m.notes || '—'}</td>
        </tr>`;
    }).join('');

    return `<div class="hr-section-card">
        <table class="hr-table">
            <thead><tr>
                <th>Medication</th><th>Dosage</th><th>Frequency</th>
                <th>Start</th><th>End</th><th>Status</th><th>Notes</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
    </div>`;
}

// ─── Visit Notes panel ─────────────────────────────────────────────────────
// Columns: Date | Staff | Summary | Notes
function buildVisitsPanel(visits) {
    if (visits.length === 0) return emptyState('No visit notes on record.');

    const rows = visits.map(v => {
        const staffName = v.USER ? v.USER.full_name : '—';
        return `<tr>
            <td style="color:#64748b;font-size:13px;white-space:nowrap;">${v.visit_date || '—'}</td>
            <td>${staffName}</td>
            <td style="font-size:13px;">${v.summary || '—'}</td>
            <td style="color:#64748b;font-size:13px;max-width:280px;">${v.raw_notes || '—'}</td>
        </tr>`;
    }).join('');

    return `<div class="hr-section-card">
        <table class="hr-table">
            <thead><tr><th>Date</th><th>Staff</th><th>Summary</th><th>Notes</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
    </div>`;
}

// ─── Helpers ───────────────────────────────────────────────────────────────
function emptyState(msg) {
    return `<div class="hr-section-card"><div class="empty-state">${msg}</div></div>`;
}

function getInitials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
}

function calcAge(dobStr) {
    if (!dobStr) return '—';
    const dob    = new Date(dobStr);
    const now    = new Date();
    let years    = now.getFullYear() - dob.getFullYear();
    let months   = now.getMonth()    - dob.getMonth();
    if (months < 0) { years--; months += 12; }
    if (years < 0)  return '—';
    if (years === 0) return `${months}m`;
    return months > 0 ? `${years}y ${months}m` : `${years}y`;
}
