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
                    MEDICAL_HISTORY (condition_name, diagnosis_date)
                `)
                .order('full_name', { ascending: true });

            if (error) {
                console.error("Error fetching health records:", error);
                container.innerHTML = `<div class="card" style="color: red;">Failed to load data</div>`;
                return;
            }

            allChildren = data || [];
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
            
            // Mock last visit since it requires joining DOCTOR_VISIT
            const lastVisit = '2026-04-15'; // Mocked
            
            // Get recent diagnosis from MEDICAL_HISTORY or mock
            let recentDiagnosis = 'Healthy - Routine Checkup';
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
                        <button class="btn btn-primary">
                            <i class="ph ph-eye"></i>
                            View Full
                        </button>
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
                        <button class="btn btn-outline" style="background-color: white;">
                            <i class="ph ph-file-text"></i> Medical History
                        </button>
                        <button class="btn btn-outline" style="background-color: white;">Treatment Records</button>
                        <button class="btn btn-outline" style="background-color: white;">Doctor Notes</button>
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', html);
        });
    }
});
