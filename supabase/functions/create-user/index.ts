import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type CreateUserBody = {
  email?: string;
  password?: string;
  role?: "admin" | "delivery_boy";
  full_name?: string;
  phone?: string;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: "Server is missing Supabase admin configuration" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const callerClient = createClient(supabaseUrl, serviceRoleKey, {
    global: { headers: { Authorization: authHeader } },
  });
  const adminClient = createClient(supabaseUrl, serviceRoleKey);

  const { data: caller } = await callerClient.auth.getUser();
  if (!caller.user) {
    return json({ error: "Unauthorized" }, 401);
  }

  const { data: profile, error: profileError } = await adminClient
    .from("profiles")
    .select("role,is_active")
    .eq("id", caller.user.id)
    .single();

  if (profileError || profile?.role !== "admin" || profile?.is_active !== true) {
    return json({ error: "Only active admins can create users" }, 403);
  }

  const body = (await req.json()) as CreateUserBody;
  if (!body.email || !body.password || !body.role || !body.full_name) {
    return json({ error: "email, password, role, and full_name are required" }, 400);
  }

  const { data: created, error: createError } = await adminClient.auth.admin.createUser({
    email: body.email,
    password: body.password,
    email_confirm: true,
  });
  if (createError || !created.user) {
    return json({ error: createError?.message ?? "Unable to create user" }, 400);
  }

  const { error: insertError } = await adminClient.from("profiles").insert({
    id: created.user.id,
    email: body.email,
    role: body.role,
    full_name: body.full_name,
    phone: body.phone ?? null,
    rate_per_delivery_paise: body.role === "delivery_boy" ? 1500 : 0,
  });

  if (insertError) {
    await adminClient.auth.admin.deleteUser(created.user.id);
    return json({ error: insertError.message }, 400);
  }

  return json({ id: created.user.id, email: body.email, role: body.role }, 201);
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
