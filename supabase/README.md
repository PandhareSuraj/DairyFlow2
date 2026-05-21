# DairyFlow Supabase Backend

Apply `migrations/202605210001_dairyflow_schema.sql` to a Supabase project, then deploy `functions/create-user`.

Required Android local properties:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-publishable-or-anon-key
GOOGLE_MAPS_API_KEY=your-google-maps-key
```

The Android app only uses the publishable/anon key. Admin user creation is routed through the Edge Function, which reads `SUPABASE_SERVICE_ROLE_KEY` from Supabase function secrets.
