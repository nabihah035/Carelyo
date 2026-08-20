document.addEventListener('DOMContentLoaded', () => {
    let allChildren = [];

    loadHealthRecords();

    document.getElementById('search-input').addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase();
        const filtered = allChildren.filter(child => {
            const childName = (child.full_name || '').toLowerCase();
            const parentName = (child.USER?.full_name || '').toLowerCase();
            return childName.includes(query) || parentName.includes(query);
        });
        renderRecords(filtered);
    });

    async function loadHealthRecords() {
        const container = document.getElementById('health-records-container');
        try {
            // Fetch children with their parent, allergies, and medical history
            const { data, error } = await window.supabaseClient
                .from('CHILD')
                .select(`
                    *,
                    USER!inner(full_name),
                    ALLERGIE (allergy_name),
                    MEDICAL_HISTORY (*),
                    DOCTOR_VISIT (*)
                `)
                .order('full_name', { ascending: true });

            if (error) {
                console.error("Error fetching health records:", error);
                container.innerHTML = `<div class="card" style="color: red;">Failed to load data</div>`;
                return;
            }

            allChildren = data || [];
            window._allChildren = allChildren;
            document.getElementById('total-children-badge').innerText = `Total Children: ${allChildren.length}`;
            renderRecords(allChildren);

        } catch (err) {
            console.error("Unexpected error:", err);
        }
    }

    function calculateAge(dob) {
        if (!dob) return 'N/A';
        const birthDate = new Date(dob);
        const today = new Date();
        let age = today.getFullYear() - birthDate.getFullYear();
        const m = today.getMonth() - birthDate.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
            age--;
        }
        return age > 0 ? `${age} years` : 'Under 1 year';
    }

    function renderRecords(data) {
        const container = document.getElementById('health-records-container');
        
        if (data.length === 0) {
            container.innerHTML = `<div class="card" style="text-align: center;">No records found.</div>`;
            return;
        }

        container.innerHTML = '';
        data.forEach(child => {
            const parentName = child.USER ? child.USER.full_name : 'Unknown Parent';
            const age = calculateAge(child.date_of_birth);
            
            let lastVisit = 'N/A';
            if (child.DOCTOR_VISIT && child.DOCTOR_VISIT.length > 0) {
                const sortedVisits = child.DOCTOR_VISIT.sort((a,b) => new Date(b.visit_date) - new Date(a.visit_date));
                lastVisit = sortedVisits[0].visit_date || 'N/A';
            }
            
            // Get recent diagnosis from MEDICAL_HISTORY or mock
            let recentDiagnosis = 'None';
            if (child.MEDICAL_HISTORY && child.MEDICAL_HISTORY.length > 0) {
                // sort by date descending
                const sortedHistory = child.MEDICAL_HISTORY.sort((a,b) => new Date(b.diagnosis_date) - new Date(a.diagnosis_date));
                recentDiagnosis = sortedHistory[0].condition_name;
            }

            let allergiesHtml = '';
            if (child.ALLERGIE && child.ALLERGIE.length > 0) {
                const allergyBadges = child.ALLERGIE.map(a => `<span style="background-color: white; color: var(--danger-color); padding: 4px 12px; border-radius: 20px; font-size: 13px; font-weight: 500;">${a.allergy_name}</span>`).join('');
                allergiesHtml = `
                    <div style="background-color: var(--danger-bg); border: 1px solid var(--danger-light); padding: 16px; border-radius: 8px; margin-bottom: 24px;">
                        <div style="color: var(--danger-color); font-weight: 500; display: flex; align-items: center; gap: 8px; margin-bottom: 12px;">
                            <i class="ph ph-warning-circle" style="font-size: 18px;"></i>
                            Allergies
                        </div>
                        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                            ${allergyBadges}
                        </div>
                    </div>
                `;
            }

            const html = `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px;">
                        <div>
                            <h3 style="font-size: 20px; margin-bottom: 4px;">${child.full_name}</h3>
                            <p style="color: var(--text-muted); font-size: 14px;">Parent: ${parentName}</p>
                        </div>
                    </div>

                    <div class="grid-4" style="gap: 16px; margin-bottom: 24px;">
                        <div style="background-color: var(--bg-main); padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Age</span>
                            <span style="font-size: 14px; font-weight: 500;">${age}</span>
                        </div>
                        <div style="background-color: var(--bg-main); padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Gender</span>
                            <span style="font-size: 14px; font-weight: 500;">${child.gender || 'N/A'}</span>
                        </div>
                        <div style="background-color: var(--bg-main); padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Last Visit</span>
                            <span style="font-size: 14px; font-weight: 500;">${lastVisit}</span>
                        </div>
                        <div style="background-color: var(--bg-main); padding: 16px; border-radius: 8px;">
                            <span style="font-size: 12px; color: var(--text-muted); display: block; margin-bottom: 4px;">Recent Diagnosis</span>
                            <span style="font-size: 14px; font-weight: 500;">${recentDiagnosis}</span>
                        </div>
                    </div>

                    ${allergiesHtml}

                    <div style="display: flex; gap: 12px;">
                        <button class="btn btn-outline" style="background-color: white;" onclick="window.showMedicalHistory(${child.childid})">
                            <i class="ph ph-file-text"></i> Medical History
                        </button>
                        <button class="btn btn-outline" style="background-color: white;" onclick="window.showTreatmentRecords(${child.childid})">
                            Treatment Records
                        </button>
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', html);
        });
    } // end renderRecords
}); // end DOMContentLoaded

window.closeRecordsModal = function() {
    document.getElementById('records-modal').style.display = 'none';
};

window.showMedicalHistory = function(childId) {
    if (!window._allChildren) return;
    const child = window._allChildren.find(c => c.childid === childId);
    if (!child) return;

    const modalTitle = document.getElementById('records-modal-title');
    const modalBody = document.getElementById('records-modal-body');
    
    modalTitle.innerText = `Medical History - ${child.full_name}`;
    
    if (!child.MEDICAL_HISTORY || child.MEDICAL_HISTORY.length === 0) {
        modalBody.innerHTML = '<p>No medical history found.</p>';
    } else {
        let html = '<ul style="padding-left: 20px;">';
        child.MEDICAL_HISTORY.forEach(record => {
            html += `<li style="margin-bottom: 12px;">
                <strong>${record.condition_name || 'Unknown Condition'}</strong> 
                <span style="color: var(--text-muted); font-size: 13px;">(${record.diagnosis_date || 'Unknown Date'})</span>
                <br/>
                <span style="font-size: 14px;">Notes: ${record.notes || 'N/A'}</span>
            </li>`;
        });
        html += '</ul>';
        modalBody.innerHTML = html;
    }
    
    document.getElementById('records-modal').style.display = 'flex';
};

window.showTreatmentRecords = function(childId) {
    if (!window._allChildren) return;
    const child = window._allChildren.find(c => c.childid === childId);
    if (!child) return;

    const modalTitle = document.getElementById('records-modal-title');
    const modalBody = document.getElementById('records-modal-body');
    
    modalTitle.innerText = `Treatment Records - ${child.full_name}`;
    
    if (!child.MEDICAL_HISTORY || child.MEDICAL_HISTORY.length === 0) {
        modalBody.innerHTML = '<p>No treatment records found.</p>';
    } else {
        const treatments = child.MEDICAL_HISTORY.filter(r => r.treatment && r.treatment.trim() !== '');
        if (treatments.length === 0) {
            modalBody.innerHTML = '<p>No treatment records found.</p>';
        } else {
            let html = '<ul style="padding-left: 20px;">';
            treatments.forEach(record => {
                html += `<li style="margin-bottom: 12px;">
                    <strong>Condition: ${record.condition_name || 'N/A'}</strong> 
                    <br/>
                    <span style="font-size: 14px;">Treatment: ${record.treatment}</span>
                </li>`;
            });
            html += '</ul>';
            modalBody.innerHTML = html;
        }
    }
    
    document.getElementById('records-modal').style.display = 'flex';
};
