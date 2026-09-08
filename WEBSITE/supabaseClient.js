// We use the UMD build of supabase-js from a CDN for this local HTML setup
// supabaseUrl and supabaseKey are provided by env.js

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
