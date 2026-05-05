import { Outlet, useNavigate, useLocation } from 'react-router';
import { Toaster } from 'sonner';
import {
  LayoutDashboard, Flag, Users, Settings, LogOut,
  Activity, ChevronRight, ArrowLeft, Smartphone,
} from 'lucide-react';
import { ReportsProvider, useReports } from './adminData';

// ── Sidebar ───────────────────────────────────────────────────────────────────
function Sidebar() {
  const navigate  = useNavigate();
  const location  = useLocation();
  const { reports } = useReports();

  const pendingCount = reports.filter((r) => r.status === 'pending').length;

  const navGroups = [
    {
      label: 'OVERVIEW',
      items: [
        { label: 'Dashboard', icon: LayoutDashboard, path: '/admin' },
      ],
    },
    {
      label: 'MANAGEMENT',
      items: [
        { label: 'Reports', icon: Flag,     path: '/admin/reports', badge: pendingCount > 0 ? pendingCount : 0 },
        { label: 'Users',   icon: Users,    path: '/admin/users'   },
      ],
    },
    {
      label: 'SYSTEM',
      items: [
        { label: 'Settings', icon: Settings, path: '/admin/settings' },
      ],
    },
  ];

  const isActive = (path: string) =>
    path === '/admin'
      ? location.pathname === '/admin'
      : location.pathname.startsWith(path);

  return (
    <aside
      style={{
        width: '256px',
        minHeight: '100vh',
        background: '#0F172A',
        display: 'flex',
        flexDirection: 'column',
        flexShrink: 0,
        position: 'sticky',
        top: 0,
        height: '100vh',
        overflowY: 'auto',
      }}
    >
      {/* Back to App */}
      <div style={{ padding: '14px 16px 0' }}>
        <button
          onClick={() => navigate('/home')}
          style={{
            width: '100%', display: 'flex', alignItems: 'center', gap: '8px',
            padding: '9px 12px', borderRadius: '10px',
            background: 'rgba(255,255,255,0.06)',
            border: '1px solid rgba(255,255,255,0.08)',
            cursor: 'pointer', transition: 'background 0.15s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.10)')}
          onMouseLeave={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.06)')}
        >
          <ArrowLeft size={14} strokeWidth={2.5} style={{ color: 'rgba(255,255,255,0.5)', flexShrink: 0 }} />
          <span style={{ fontSize: '12px', fontWeight: 600, color: 'rgba(255,255,255,0.5)', flex: 1, textAlign: 'left' }}>
            Back to App
          </span>
          <Smartphone size={13} strokeWidth={2} style={{ color: 'rgba(255,255,255,0.25)', flexShrink: 0 }} />
        </button>
      </div>

      {/* Brand */}
      <div
        style={{
          padding: '16px 20px 20px',
          borderBottom: '1px solid rgba(255,255,255,0.07)',
          marginTop: '10px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '11px' }}>
          <div
            style={{
              width: '38px', height: '38px', borderRadius: '11px',
              background: 'linear-gradient(135deg, #F97316, #FB923C)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 4px 12px rgba(249,115,22,0.40)',
            }}
          >
            <Activity size={19} strokeWidth={2.5} style={{ color: 'white' }} />
          </div>
          <div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: 'white', letterSpacing: '-0.3px' }}>
              WalkMate
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginTop: '2px' }}>
              <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#22C55E' }} />
              <span style={{ fontSize: '10px', color: 'rgba(255,255,255,0.4)', fontWeight: 600, letterSpacing: '0.8px' }}>
                ADMIN PANEL
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav style={{ flex: 1, padding: '16px 12px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        {navGroups.map((group) => (
          <div key={group.label}>
            <div
              style={{
                fontSize: '10px', fontWeight: 700, color: 'rgba(255,255,255,0.25)',
                letterSpacing: '1px', padding: '0 8px', marginBottom: '6px',
              }}
            >
              {group.label}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
              {group.items.map((item) => {
                const active = isActive(item.path);
                return (
                  <button
                    key={item.label}
                    onClick={() => navigate(item.path)}
                    style={{
                      width: '100%', display: 'flex', alignItems: 'center', gap: '10px',
                      padding: '9px 12px', borderRadius: '10px', border: 'none', cursor: 'pointer',
                      background: active ? 'rgba(249,115,22,0.15)' : 'transparent',
                      transition: 'background 0.15s',
                    }}
                    onMouseEnter={(e) => {
                      if (!active) e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
                    }}
                    onMouseLeave={(e) => {
                      if (!active) e.currentTarget.style.background = 'transparent';
                    }}
                  >
                    <item.icon
                      size={17}
                      strokeWidth={active ? 2.5 : 2}
                      style={{ color: active ? '#F97316' : 'rgba(255,255,255,0.45)', flexShrink: 0 }}
                    />
                    <span
                      style={{
                        fontSize: '13px', fontWeight: active ? 700 : 400, flex: 1, textAlign: 'left',
                        color: active ? 'white' : 'rgba(255,255,255,0.55)',
                      }}
                    >
                      {item.label}
                    </span>
                    {(item as any).badge ? (
                      <div
                        style={{
                          background: active ? '#F97316' : 'rgba(249,115,22,0.25)',
                          color: 'white', borderRadius: '20px', padding: '1px 8px',
                          fontSize: '11px', fontWeight: 700, minWidth: '22px', textAlign: 'center',
                        }}
                      >
                        {(item as any).badge}
                      </div>
                    ) : active ? (
                      <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#F97316' }} />
                    ) : null}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      {/* Divider */}
      <div style={{ height: '1px', background: 'rgba(255,255,255,0.07)', margin: '0 16px' }} />

      {/* Admin profile */}
      <div style={{ padding: '16px 12px 24px' }}>
        <div
          style={{
            display: 'flex', alignItems: 'center', gap: '10px',
            padding: '10px 12px', borderRadius: '10px',
            background: 'rgba(255,255,255,0.05)', cursor: 'pointer',
          }}
        >
          <div
            style={{
              width: '34px', height: '34px', borderRadius: '10px', flexShrink: 0,
              background: 'linear-gradient(135deg, #7C3AED, #A78BFA)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <span style={{ fontSize: '12px', fontWeight: 700, color: 'white' }}>SA</span>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: '13px', fontWeight: 600, color: 'white', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              Super Admin
            </div>
            <div style={{ fontSize: '11px', color: 'rgba(255,255,255,0.35)' }}>
              admin@walkmate.app
            </div>
          </div>
          <LogOut size={14} style={{ color: 'rgba(255,255,255,0.25)', flexShrink: 0 }} strokeWidth={2} />
        </div>
      </div>
    </aside>
  );
}

// ── Dashboard placeholder ─────────────────────────────────────────────────────
function AdminDashboard() {
  const navigate = useNavigate();
  const { reports } = useReports();
  const pending = reports.filter((r) => r.status === 'pending').length;

  return (
    <div style={{ padding: '40px 48px', maxWidth: '960px' }}>
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '26px', fontWeight: 800, color: '#0F172A', letterSpacing: '-0.4px' }}>
          Dashboard
        </h1>
        <p style={{ fontSize: '14px', color: '#64748B', marginTop: '4px' }}>
          Welcome back, Super Admin. Here's what needs your attention.
        </p>
      </div>

      {/* Quick stat */}
      {pending > 0 && (
        <div
          onClick={() => navigate('/admin/reports?tab=pending')}
          style={{
            background: '#FFFBEB', border: '1px solid #FDE68A',
            borderRadius: '16px', padding: '20px 24px',
            display: 'flex', alignItems: 'center', gap: '16px',
            cursor: 'pointer', marginBottom: '24px',
          }}
        >
          <div
            style={{
              width: '48px', height: '48px', borderRadius: '14px',
              background: '#FEF3C7', display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <Flag size={22} strokeWidth={2} style={{ color: '#D97706' }} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '15px', fontWeight: 700, color: '#92400E' }}>
              {pending} Report{pending !== 1 ? 's' : ''} Awaiting Review
            </div>
            <div style={{ fontSize: '13px', color: '#D97706', marginTop: '2px' }}>
              Click to review pending reports →
            </div>
          </div>
          <ChevronRight size={18} strokeWidth={2.5} style={{ color: '#D97706' }} />
        </div>
      )}

      <div
        style={{
          background: 'white', borderRadius: '16px', border: '1px solid #E2E8F0',
          padding: '32px', display: 'flex', flexDirection: 'column', alignItems: 'center',
          color: '#94A3B8', gap: '8px',
        }}
      >
        <LayoutDashboard size={32} strokeWidth={1.5} />
        <p style={{ fontSize: '15px', fontWeight: 600, color: '#475569' }}>More dashboard widgets coming soon</p>
        <p style={{ fontSize: '13px' }}>Navigate to Reports using the sidebar to manage user-submitted reports.</p>
      </div>
    </div>
  );
}

// ── Root layout ───────────────────────────────────────────────────────────────
function AdminRoot() {
  const location = useLocation();
  const isDashboard = location.pathname === '/admin';

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: '#F1F5F9' }}>
      <Sidebar />
      <main style={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>
        {isDashboard ? <AdminDashboard /> : <Outlet />}
      </main>
    </div>
  );
}

export function AdminLayout() {
  return (
    <ReportsProvider>
      <AdminRoot />
      <Toaster position="top-right" richColors duration={4000} />
    </ReportsProvider>
  );
}