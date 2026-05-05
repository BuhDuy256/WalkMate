import { useNavigate } from 'react-router';
import {
  Settings, Eye, History, Award, Trophy, Cog,
  Users, ShieldOff, LogOut, ChevronRight, Pencil,
  Footprints, ShieldCheck, LayoutDashboard, Flag,
} from 'lucide-react';
import { BottomNav } from '../layout/BottomNav';

const AVATAR_URL =
  'https://images.unsplash.com/photo-1761933808230-9a2e78956daa?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx5b3VuZyUyMGFzaWFuJTIwd29tYW4lMjBwb3J0cmFpdCUyMHNtaWxpbmd8ZW58MXx8fHwxNzc3NDMyODUzfDA&ixlib=rb-4.1.0&q=80&w=400';

const menuItems = [
  { id: 'history', icon: History, label: 'Walk History & Disputes', path: '/walk-history', emoji: '🕐' },
  { id: 'badges', icon: Award, label: 'My Badges', path: '/profile/badges', emoji: '🏆' },
  { id: 'leaderboard', icon: Trophy, label: 'Leaderboard', path: '/leaderboard', emoji: '🔥' },
  { id: 'settings', icon: Cog, label: 'Settings', path: '/profile/settings', emoji: '⚙️' },
  { id: 'friends', icon: Users, label: 'Friends', path: '/friends', emoji: '⭐' },
  { id: 'blocked', icon: ShieldOff, label: 'Blocked Users', path: '/profile/blocked', emoji: '⋮' },
];

// Simulated: this user has admin access
const IS_ADMIN = true;
const ADMIN_PENDING_COUNT = 4;

export function ProfileScreen() {
  const navigate = useNavigate();

  return (
    <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5', overflowY: 'auto' }}>
      {/* Header */}
      <div
        className="flex items-center justify-between px-6 pt-14 pb-4"
        style={{ background: '#FEF9F5' }}
      >
        <div style={{ width: 40 }} />
        <h1 style={{ fontSize: '18px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
          Profile
        </h1>
        <button
          style={{
            width: '40px', height: '40px', background: 'white',
            borderRadius: '12px', border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer',
          }}
        >
          <Settings size={18} style={{ color: '#44403C' }} strokeWidth={2} />
        </button>
      </div>

      {/* Avatar + Name section */}
      <div className="flex flex-col items-center px-6 pb-5">
        {/* Avatar */}
        <div
          style={{
            width: '88px', height: '88px', borderRadius: '50%',
            overflow: 'hidden',
            border: '3px solid white',
            boxShadow: '0 6px 20px rgba(0,0,0,0.14)',
            marginBottom: '12px',
            flexShrink: 0,
          }}
        >
          <img src={AVATAR_URL} alt="Avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        </div>

        {/* Name + admin badge */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
          <h2 style={{ fontSize: '22px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
            Luân Trần
          </h2>
          {IS_ADMIN && (
            <div
              style={{
                display: 'flex', alignItems: 'center', gap: '4px',
                background: '#0F172A', borderRadius: '8px', padding: '3px 8px',
              }}
            >
              <ShieldCheck size={10} strokeWidth={2.5} style={{ color: '#F97316' }} />
              <span style={{ fontSize: '10px', fontWeight: 800, color: '#F97316', letterSpacing: '0.5px' }}>
                ADMIN
              </span>
            </div>
          )}
        </div>

        {/* View as Public Profile */}
        <button
          onClick={() => navigate('/public-profile')}
          className="flex items-center gap-1.5"
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            marginBottom: '14px',
          }}
        >
          <Eye size={13} style={{ color: '#78716C' }} strokeWidth={2} />
          <span style={{ fontSize: '13px', color: '#78716C', fontWeight: 500 }}>View as Public Profile</span>
        </button>

        {/* Points badge */}
        <div
          className="flex items-center gap-1.5"
          style={{
            border: '1.5px solid #F97316',
            borderRadius: '100px',
            padding: '5px 16px',
            marginBottom: '6px',
          }}
        >
          <span style={{ fontSize: '16px' }}>⭐</span>
          <span style={{ fontSize: '14px', fontWeight: 700, color: '#1C1917' }}>530 pts</span>
        </div>

        {/* Tier label */}
        <span style={{ fontSize: '13px', fontWeight: 500, color: '#78716C', marginBottom: '10px' }}>
          Standard
        </span>

        {/* Personality tag */}
        <div
          style={{
            background: '#FFF7ED',
            border: '1.5px solid #FED7AA',
            borderRadius: '100px',
            padding: '4px 14px',
            marginBottom: '16px',
          }}
        >
          <span style={{ fontSize: '13px', fontWeight: 600, color: '#F97316' }}>Power Walking</span>
        </div>

        {/* Edit Profile button */}
        <button
          onClick={() => navigate('/edit-profile')}
          className="flex items-center justify-center gap-2"
          style={{
            background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
            border: 'none', borderRadius: '100px',
            padding: '13px 44px',
            fontSize: '15px', fontWeight: 700, color: 'white',
            cursor: 'pointer',
            boxShadow: '0 6px 20px rgba(249,115,22,0.38)',
            letterSpacing: '-0.1px',
          }}
        >
          <Pencil size={15} strokeWidth={2.5} />
          Edit Profile
        </button>
      </div>

      {/* Stats card */}
      <div className="px-5 mb-4">
        <div
          style={{
            background: 'white',
            borderRadius: '20px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
            overflow: 'hidden',
          }}
        >
          {/* Top row: KM | Sessions */}
          <div className="flex" style={{ borderBottom: '1px solid #F3F2F0' }}>
            <div
              className="flex-1 flex flex-col items-center py-5"
              style={{ borderRight: '1px solid #F3F2F0' }}
            >
              <div
                className="flex items-center justify-center mb-2"
                style={{
                  width: '36px', height: '36px',
                  background: '#FFF7ED', borderRadius: '11px',
                }}
              >
                <Footprints size={18} style={{ color: '#F97316' }} strokeWidth={1.8} />
              </div>
              <span style={{ fontSize: '26px', fontWeight: 800, color: '#1C1917', lineHeight: 1 }}>0</span>
              <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500, marginTop: '4px' }}>Total KM</span>
            </div>
            <div className="flex-1 flex flex-col items-center py-5">
              <div
                className="flex items-center justify-center mb-2"
                style={{
                  width: '36px', height: '36px',
                  background: '#FFF7ED', borderRadius: '11px',
                }}
              >
                <Users size={18} style={{ color: '#F97316' }} strokeWidth={1.8} />
              </div>
              <span style={{ fontSize: '26px', fontWeight: 800, color: '#1C1917', lineHeight: 1 }}>2</span>
              <span style={{ fontSize: '12px', color: '#A8A29E', fontWeight: 500, marginTop: '4px' }}>Sessions</span>
            </div>
          </div>

          {/* Achievements row — removed; see My Badges page */}
        </div>
      </div>

      {/* ── Admin Dashboard Card (admin users only) ── */}
      {IS_ADMIN && (
        <div className="px-5 mb-4">
          <div
            style={{
              background: 'linear-gradient(135deg, #0F172A 0%, #1E2D3D 100%)',
              borderRadius: '20px',
              border: '1.5px solid rgba(249,115,22,0.28)',
              boxShadow: '0 8px 28px rgba(15,23,42,0.22)',
              overflow: 'hidden',
              padding: '18px',
            }}
          >
            {/* Top row: icon + title + pending badge */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '11px' }}>
                <div
                  style={{
                    width: '40px', height: '40px', borderRadius: '13px',
                    background: 'linear-gradient(135deg, #F97316, #FB923C)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    boxShadow: '0 4px 14px rgba(249,115,22,0.45)',
                    flexShrink: 0,
                  }}
                >
                  <ShieldCheck size={19} strokeWidth={2.5} style={{ color: 'white' }} />
                </div>
                <div>
                  <div style={{ fontSize: '15px', fontWeight: 800, color: 'white', letterSpacing: '-0.2px' }}>
                    Admin Dashboard
                  </div>
                  <div style={{ fontSize: '11px', color: 'rgba(255,255,255,0.42)', marginTop: '1px' }}>
                    Super Admin · Elevated Access
                  </div>
                </div>
              </div>
              {/* Pending badge */}
              {ADMIN_PENDING_COUNT > 0 && (
                <div
                  style={{
                    background: '#F97316', borderRadius: '20px',
                    padding: '3px 10px', flexShrink: 0,
                  }}
                >
                  <span style={{ fontSize: '11px', fontWeight: 800, color: 'white' }}>
                    {ADMIN_PENDING_COUNT} pending
                  </span>
                </div>
              )}
            </div>

            {/* Divider */}
            <div style={{ height: '1px', background: 'rgba(255,255,255,0.08)', marginBottom: '14px' }} />

            {/* Quick stats row */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Flag size={12} strokeWidth={2} style={{ color: '#F59E0B' }} />
                <span style={{ fontSize: '12px', color: 'rgba(255,255,255,0.55)', fontWeight: 500 }}>
                  {ADMIN_PENDING_COUNT} reports awaiting review
                </span>
              </div>
            </div>

            {/* CTA button */}
            <button
              onClick={() => navigate('/admin/reports')}
              style={{
                width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                padding: '13px',
                background: 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                border: 'none', borderRadius: '14px', cursor: 'pointer',
                fontSize: '14px', fontWeight: 800, color: 'white',
                boxShadow: '0 6px 20px rgba(249,115,22,0.42)',
                letterSpacing: '-0.1px',
              }}
            >
              <LayoutDashboard size={16} strokeWidth={2.5} />
              Open Admin Panel
              <ChevronRight size={16} strokeWidth={2.5} />
            </button>
          </div>
        </div>
      )}

      {/* Menu list */}
      <div className="px-5 mb-4">
        <div
          style={{
            background: 'white',
            borderRadius: '20px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
            overflow: 'hidden',
          }}
        >
          {menuItems.map((item, idx) => (
            <button
              key={item.id}
              onClick={() => navigate(item.path)}
              className="w-full flex items-center gap-3"
              style={{
                padding: '15px 18px',
                background: 'transparent',
                border: 'none',
                borderBottom: idx < menuItems.length - 1 ? '1px solid #F3F2F0' : 'none',
                cursor: 'pointer',
                textAlign: 'left',
              }}
            >
              <div
                style={{
                  width: '34px', height: '34px',
                  background: '#FFF7ED', borderRadius: '10px',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <item.icon size={17} style={{ color: '#F97316' }} strokeWidth={2} />
              </div>
              <span style={{ flex: 1, fontSize: '15px', fontWeight: 600, color: '#1C1917' }}>
                {item.label}
              </span>
              <ChevronRight size={17} style={{ color: '#A8A29E' }} strokeWidth={2} />
            </button>
          ))}
        </div>
      </div>

      {/* Log Out All Devices */}
      <div className="px-5 mb-5">
        <div
          style={{
            background: 'white',
            borderRadius: '20px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
            overflow: 'hidden',
          }}
        >
          <button
            className="w-full flex items-center gap-3"
            style={{
              padding: '15px 18px',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div
              style={{
                width: '34px', height: '34px',
                background: '#FFF1F0', borderRadius: '10px',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <LogOut size={17} style={{ color: '#EF4444' }} strokeWidth={2} />
            </div>
            <span style={{ flex: 1, fontSize: '15px', fontWeight: 600, color: '#EF4444' }}>
              Log Out All Devices
            </span>
          </button>
        </div>
      </div>

      {/* Bottom spacing */}
      <div style={{ height: '80px' }} />
      <BottomNav />
    </div>
  );
}