create extension if not exists pgcrypto with schema extensions;
create extension if not exists postgis with schema extensions;

do $$ begin
  create type public.user_role as enum ('admin', 'delivery_boy');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.delivery_status as enum ('pending', 'done', 'skipped');
exception when duplicate_object then null;
end $$;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  role public.user_role not null default 'delivery_boy',
  full_name text not null,
  email text not null,
  phone text,
  rating numeric(3, 2) not null default 0,
  total_deliveries integer not null default 0,
  on_time_rate numeric(5, 2) not null default 0,
  streak integer not null default 0,
  rate_per_delivery_paise integer not null default 1500,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  unit text not null default 'L',
  price_paise integer not null check (price_paise >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text,
  address text not null,
  latitude double precision not null,
  longitude double precision not null,
  location geography(point, 4326) generated always as (
    st_setsrid(st_makepoint(longitude, latitude), 4326)::geography
  ) stored,
  product_id uuid not null references public.products(id),
  quantity numeric(10, 2) not null default 1,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.assignments (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers(id),
  delivery_boy_id uuid not null references public.profiles(id),
  product_id uuid not null references public.products(id),
  quantity numeric(10, 2) not null default 1,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (customer_id, delivery_boy_id, product_id)
);

create table if not exists public.deliveries (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.assignments(id),
  customer_id uuid not null references public.customers(id),
  delivery_boy_id uuid not null references public.profiles(id),
  product_id uuid not null references public.products(id),
  delivery_date date not null default current_date,
  status public.delivery_status not null default 'pending',
  completed_at timestamptz,
  skipped_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (assignment_id, delivery_date)
);

create table if not exists public.skipped_deliveries (
  id uuid primary key default gen_random_uuid(),
  delivery_id uuid not null references public.deliveries(id) on delete cascade,
  customer_id uuid not null references public.customers(id),
  delivery_boy_id uuid not null references public.profiles(id),
  skipped_date date not null,
  reason text,
  created_at timestamptz not null default now(),
  unique (customer_id, skipped_date)
);

create table if not exists public.invoices (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers(id),
  month date not null,
  subtotal_paise integer not null default 0,
  previous_pending_paise integer not null default 0,
  total_paise integer not null default 0,
  status text not null default 'pending' check (status in ('pending', 'paid', 'void')),
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  unique (customer_id, month)
);

create table if not exists public.invoice_items (
  id uuid primary key default gen_random_uuid(),
  invoice_id uuid not null references public.invoices(id) on delete cascade,
  product_id uuid not null references public.products(id),
  description text not null,
  quantity numeric(12, 2) not null,
  unit_price_paise integer not null,
  amount_paise integer not null
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  invoice_id uuid not null references public.invoices(id),
  customer_id uuid not null references public.customers(id),
  amount_paise integer not null check (amount_paise > 0),
  paid_at timestamptz not null default now(),
  created_by uuid references public.profiles(id)
);

create table if not exists public.app_settings (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin' and is_active
  );
$$;

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists touch_profiles_updated_at on public.profiles;
create trigger touch_profiles_updated_at before update on public.profiles
for each row execute function public.touch_updated_at();

drop trigger if exists touch_products_updated_at on public.products;
create trigger touch_products_updated_at before update on public.products
for each row execute function public.touch_updated_at();

drop trigger if exists touch_customers_updated_at on public.customers;
create trigger touch_customers_updated_at before update on public.customers
for each row execute function public.touch_updated_at();

drop trigger if exists touch_deliveries_updated_at on public.deliveries;
create trigger touch_deliveries_updated_at before update on public.deliveries
for each row execute function public.touch_updated_at();

create or replace function public.record_skipped_delivery()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'skipped' then
    insert into public.skipped_deliveries (
      delivery_id,
      customer_id,
      delivery_boy_id,
      skipped_date,
      reason
    )
    values (
      new.id,
      new.customer_id,
      new.delivery_boy_id,
      new.delivery_date,
      new.skipped_reason
    )
    on conflict (customer_id, skipped_date) do update
      set delivery_id = excluded.delivery_id,
          delivery_boy_id = excluded.delivery_boy_id,
          reason = excluded.reason;
  end if;
  return new;
end;
$$;

drop trigger if exists deliveries_record_skips on public.deliveries;
create trigger deliveries_record_skips
after insert or update of status, skipped_reason on public.deliveries
for each row execute function public.record_skipped_delivery();

alter table public.profiles enable row level security;
alter table public.products enable row level security;
alter table public.customers enable row level security;
alter table public.assignments enable row level security;
alter table public.deliveries enable row level security;
alter table public.skipped_deliveries enable row level security;
alter table public.invoices enable row level security;
alter table public.invoice_items enable row level security;
alter table public.payments enable row level security;
alter table public.app_settings enable row level security;

drop policy if exists "profiles admin all" on public.profiles;
create policy "profiles admin all" on public.profiles
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "profiles own read" on public.profiles;
create policy "profiles own read" on public.profiles
for select using (id = auth.uid());

drop policy if exists "products admin write read staff read" on public.products;
create policy "products admin write read staff read" on public.products
for select using (public.is_admin() or auth.role() = 'authenticated');

drop policy if exists "products admin mutate" on public.products;
create policy "products admin mutate" on public.products
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "customers admin all" on public.customers;
create policy "customers admin all" on public.customers
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "customers staff assigned read" on public.customers;
create policy "customers staff assigned read" on public.customers
for select using (
  exists (
    select 1 from public.assignments a
    where a.customer_id = customers.id
      and a.delivery_boy_id = auth.uid()
      and a.is_active
  )
);

drop policy if exists "assignments admin all" on public.assignments;
create policy "assignments admin all" on public.assignments
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "assignments staff read own" on public.assignments;
create policy "assignments staff read own" on public.assignments
for select using (delivery_boy_id = auth.uid());

drop policy if exists "deliveries admin all" on public.deliveries;
create policy "deliveries admin all" on public.deliveries
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "deliveries staff own read" on public.deliveries;
create policy "deliveries staff own read" on public.deliveries
for select using (delivery_boy_id = auth.uid());

drop policy if exists "deliveries staff own status update" on public.deliveries;
create policy "deliveries staff own status update" on public.deliveries
for update using (delivery_boy_id = auth.uid())
with check (delivery_boy_id = auth.uid());

drop policy if exists "skipped admin read" on public.skipped_deliveries;
create policy "skipped admin read" on public.skipped_deliveries
for select using (public.is_admin());

drop policy if exists "skipped staff own read" on public.skipped_deliveries;
create policy "skipped staff own read" on public.skipped_deliveries
for select using (delivery_boy_id = auth.uid());

drop policy if exists "invoices admin all" on public.invoices;
create policy "invoices admin all" on public.invoices
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "invoice items admin all" on public.invoice_items;
create policy "invoice items admin all" on public.invoice_items
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "payments admin all" on public.payments;
create policy "payments admin all" on public.payments
for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "settings admin all" on public.app_settings;
create policy "settings admin all" on public.app_settings
for all using (public.is_admin()) with check (public.is_admin());

create or replace view public.dashboard_summary as
select
  coalesce((select sum(total_paise) from public.invoices where status = 'paid'), 0)::integer as revenue_paise,
  (select count(*) from public.products where is_active)::integer as total_products,
  (select count(*) from public.customers where is_active)::integer as active_customers,
  (select count(*) from public.profiles where role = 'delivery_boy' and is_active)::integer as delivery_staff,
  (select count(*) from public.deliveries where delivery_date = current_date and status = 'pending')::integer as pending_deliveries;

create or replace function public.today_route(target_staff uuid default auth.uid())
returns table (
  route_order integer,
  delivery_id uuid,
  customer_id uuid,
  customer_name text,
  address text,
  latitude double precision,
  longitude double precision,
  status public.delivery_status
)
language sql
stable
security definer
set search_path = public
as $$
  select
    row_number() over (order by c.location <-> st_setsrid(st_makepoint(77.2090, 28.6139), 4326)::geography)::integer,
    d.id,
    c.id,
    c.name,
    c.address,
    c.latitude,
    c.longitude,
    d.status
  from public.deliveries d
  join public.customers c on c.id = d.customer_id
  where d.delivery_boy_id = target_staff
    and d.delivery_date = current_date
    and (target_staff = auth.uid() or public.is_admin());
$$;

create or replace function public.generate_monthly_invoices(target_month date default date_trunc('month', now())::date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  month_start date := date_trunc('month', target_month)::date;
  month_end date := (date_trunc('month', target_month) + interval '1 month')::date;
  customer_record record;
  generated_invoice_id uuid;
  subtotal integer;
  previous_pending integer;
begin
  if not public.is_admin() then
    raise exception 'Only admins can generate invoices';
  end if;

  for customer_record in
    select id from public.customers where is_active
  loop
    select coalesce(sum(round(a.quantity * p.price_paise)::integer), 0)
      into subtotal
    from public.deliveries d
    join public.assignments a on a.id = d.assignment_id
    join public.products p on p.id = d.product_id
    where d.customer_id = customer_record.id
      and d.delivery_date >= month_start
      and d.delivery_date < month_end
      and d.status = 'done';

    select greatest(
      coalesce(sum(i.total_paise), 0) - coalesce(sum(pay.amount_paise), 0),
      0
    )::integer
      into previous_pending
    from public.invoices i
    left join public.payments pay on pay.invoice_id = i.id
    where i.customer_id = customer_record.id
      and i.month < month_start
      and i.status <> 'void';

    insert into public.invoices (customer_id, month, subtotal_paise, previous_pending_paise, total_paise)
    values (customer_record.id, month_start, subtotal, previous_pending, subtotal + previous_pending)
    on conflict (customer_id, month) do update
      set subtotal_paise = excluded.subtotal_paise,
          previous_pending_paise = excluded.previous_pending_paise,
          total_paise = excluded.total_paise
    returning id into generated_invoice_id;

    delete from public.invoice_items where invoice_id = generated_invoice_id;

    insert into public.invoice_items (invoice_id, product_id, description, quantity, unit_price_paise, amount_paise)
    select
      generated_invoice_id,
      p.id,
      p.name || ' delivered days',
      sum(a.quantity),
      p.price_paise,
      sum(round(a.quantity * p.price_paise)::integer)
    from public.deliveries d
    join public.assignments a on a.id = d.assignment_id
    join public.products p on p.id = d.product_id
    where d.customer_id = customer_record.id
      and d.delivery_date >= month_start
      and d.delivery_date < month_end
      and d.status = 'done'
    group by p.id, p.name, p.price_paise;
  end loop;

  return (select count(*)::integer from public.invoices where month = month_start);
end;
$$;

create or replace function public.mark_invoice_paid(target_invoice uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  invoice_record public.invoices%rowtype;
begin
  if not public.is_admin() then
    raise exception 'Only admins can record payments';
  end if;

  select * into invoice_record from public.invoices where id = target_invoice;
  if not found then
    raise exception 'Invoice not found';
  end if;

  insert into public.payments (invoice_id, customer_id, amount_paise, created_by)
  values (invoice_record.id, invoice_record.customer_id, invoice_record.total_paise, auth.uid());

  update public.invoices
  set status = 'paid', paid_at = now()
  where id = invoice_record.id;
end;
$$;
