// We use the UMD build of supabase-js from a CDN for this local HTML setup
const supabaseUrl = 'https://hrwppmgrlitutjbqzekt.supabase.co';
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhyd3BwbWdybGl0dXRqYnF6ZWt0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc5NzM4NjYsImV4cCI6MjA5MzU0OTg2Nn0.jYZ2VvhOYyHpCh6p_zaofe6XlRXoQxEh9MwcecOUB74';

// window.supabase is available globally if we load the CDN script before this
window.supabaseClient = window.supabase.createClient(supabaseUrl, supabaseKey);

// Optional: Test connection
async function testConnection() {
  const { data, error } = await window.supabaseClient.from('USER').select('userid').limit(1);
  if (error) {
    console.error('Supabase connection error:', error);
  } else {
    console.log('Supabase connected successfully!');
  }
}

// testConnection();
