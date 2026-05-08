import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ArrowLeft, Eye, EyeOff, Lock, Shield,
  Chrome, CheckCircle2, KeyRound,
} from 'lucide-react';

// ── Login method pills ───────────────────────────────────────────────────────
const LOGIN_METHODS = [
  { id: 'password', label: 'Password', status: 'Set',       Icon: KeyRound, color: '#F97316', bg: '#FFF7ED', border: '#FED7AA' },
  { id: 'google',   label: 'Google',   status: 'Connected', Icon: Chrome,   color: '#10B981', bg: '#ECFDF5', border: '#6EE7B7' },
];

// ── Single password field ────────────────────────────────────────────────────
function PasswordField({
  label,
  value,
  onChange,
  placeholder,
  error,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  error?: string;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div>
      <label
        style={{
          display: 'block',
          fontSize: '13px',
          fontWeight: 700,
          color: '#57534E',
          marginBottom: '8px',
          letterSpacing: '-0.1px',
        }}
      >
        {label}
      </label>
      <div
        style={{
          position: 'relative',
          background: error ? '#FFF5F5' : '#FAFAF9',
          border: `1.5px solid ${error ? '#FCA5A5' : value ? '#F97316' : '#F3F2F0'}`,
          borderRadius: '16px',
          transition: 'border-color 0.15s',
          boxShadow: error
            ? '0 0 0 3px rgba(239,68,68,0.08)'
            : value
            ? '0 0 0 3px rgba(249,115,22,0.09)'
            : 'none',
        }}
      >
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          style={{
            width: '100%',
            padding: '14px 50px 14px 18px',
            background: 'transparent',
            border: 'none',
            outline: 'none',
            fontSize: '15px',
            color: '#1C1917',
            fontFamily: 'Inter, sans-serif',
            boxSizing: 'border-box',
          }}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          style={{
            position: 'absolute',
            right: '14px',
            top: '50%',
            transform: 'translateY(-50%)',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: '4px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {visible
            ? <Eye size={18} strokeWidth={2} style={{ color: '#F97316' }} />
            : <EyeOff size={18} strokeWidth={2} style={{ color: '#A8A29E' }} />
          }
        </button>
      </div>
      {error && (
        <p style={{ fontSize: '12px', color: '#EF4444', marginTop: '5px', fontWeight: 500 }}>
          {error}
        </p>
      )}
    </div>
  );
}

// ── Main screen ──────────────────────────────────────────────────────────────
export function ResetPasswordScreen() {
  const navigate = useNavigate();

  const [current,  setCurrent]  = useState('');
  const [next,     setNext]     = useState('');
  const [confirm,  setConfirm]  = useState('');
  const [errors,   setErrors]   = useState<Record<string, string>>({});
  const [success,  setSuccess]  = useState(false);
  const [loading,  setLoading]  = useState(false);

  function validate() {
    const e: Record<string, string> = {};
    if (!current)               e.current = 'Please enter your current password.';
    if (next.length < 8)        e.next    = 'New password must be at least 8 characters.';
    if (next !== confirm)       e.confirm = 'Passwords do not match.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function handleSubmit() {
    if (!validate()) return;
    setLoading(true);
    // Simulate async request
    setTimeout(() => {
      setLoading(false);
      setSuccess(true);
      setCurrent(''); setNext(''); setConfirm('');
    }, 1200);
  }

  // ── Password strength ──────────────────────────────────────────────────────
  const strength =
    next.length === 0 ? 0 :
    next.length < 6   ? 1 :
    next.length < 10  ? 2 : 3;

  const strengthLabel = ['', 'Weak', 'Good', 'Strong'];
  const strengthColor = ['', '#EF4444', '#F59E0B', '#10B981'];

  return (
    <div
      className="w-full h-full flex flex-col"
      style={{ background: '#FEF9F5', overflowY: 'auto' }}
    >
      {/* ── Header ── */}
      <div
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '56px 20px 16px',
          background: '#FEF9F5',
          position: 'sticky', top: 0, zIndex: 10,
        }}
      >
        <button
          onClick={() => navigate(-1)}
          style={{
            width: 40, height: 40, borderRadius: 12,
            background: 'white', border: '1.5px solid #F3F2F0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer',
          }}
        >
          <ArrowLeft size={18} strokeWidth={2.5} style={{ color: '#1C1917' }} />
        </button>

        <h1 style={{ fontSize: 18, fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
          Security
        </h1>

        {/* Shield icon — decorative balance */}
        <div
          style={{
            width: 40, height: 40, borderRadius: 12,
            background: '#FFF7ED', border: '1.5px solid #FED7AA',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Shield size={18} strokeWidth={2} style={{ color: '#F97316' }} />
        </div>
      </div>

      <div style={{ padding: '4px 20px 100px', display: 'flex', flexDirection: 'column', gap: 16 }}>

        {/* ── Login Methods card ── */}
        <div
          style={{
            background: 'white', borderRadius: 20,
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 4px 16px rgba(0,0,0,0.05)',
            overflow: 'hidden',
          }}
        >
          {/* Section label */}
          <div
            style={{
              padding: '14px 18px 10px',
              borderBottom: '1px solid #F3F2F0',
            }}
          >
            <span
              style={{
                fontSize: 10, fontWeight: 800, color: '#A8A29E',
                letterSpacing: '1px', textTransform: 'uppercase',
              }}
            >
              Login Methods
            </span>
          </div>

          {/* Method rows */}
          {LOGIN_METHODS.map((m, idx) => (
            <div
              key={m.id}
              style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '13px 18px',
                borderBottom: idx < LOGIN_METHODS.length - 1 ? '1px solid #F3F2F0' : 'none',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div
                  style={{
                    width: 34, height: 34, borderRadius: 10,
                    background: m.bg, border: `1.5px solid ${m.border}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <m.Icon size={16} strokeWidth={2} style={{ color: m.color }} />
                </div>
                <span style={{ fontSize: 15, fontWeight: 600, color: '#1C1917' }}>
                  {m.label}
                </span>
              </div>
              <div
                style={{
                  display: 'flex', alignItems: 'center', gap: 5,
                  background: m.bg, borderRadius: 20,
                  padding: '4px 10px', border: `1px solid ${m.border}`,
                }}
              >
                <CheckCircle2 size={12} strokeWidth={2.5} style={{ color: m.color }} />
                <span style={{ fontSize: 11, fontWeight: 700, color: m.color }}>
                  {m.status}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* ── Change Password card ── */}
        <div
          style={{
            background: 'white', borderRadius: 20,
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 4px 16px rgba(0,0,0,0.05)',
            overflow: 'hidden',
          }}
        >
          {/* Section label */}
          <div
            style={{
              padding: '14px 18px 10px',
              borderBottom: '1px solid #F3F2F0',
              display: 'flex', alignItems: 'center', gap: 8,
            }}
          >
            <Lock size={13} strokeWidth={2.5} style={{ color: '#F97316' }} />
            <span
              style={{
                fontSize: 10, fontWeight: 800, color: '#A8A29E',
                letterSpacing: '1px', textTransform: 'uppercase',
              }}
            >
              Change Password
            </span>
          </div>

          <div style={{ padding: '18px 18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>

            {/* Success banner */}
            {success && (
              <div
                style={{
                  display: 'flex', alignItems: 'center', gap: 10,
                  background: '#ECFDF5', border: '1.5px solid #6EE7B7',
                  borderRadius: 14, padding: '12px 14px',
                }}
              >
                <CheckCircle2 size={18} strokeWidth={2.5} style={{ color: '#10B981', flexShrink: 0 }} />
                <span style={{ fontSize: 13, fontWeight: 600, color: '#065F46' }}>
                  Password updated successfully!
                </span>
              </div>
            )}

            <PasswordField
              label="Current Password"
              value={current}
              onChange={(v) => { setCurrent(v); setErrors((e) => ({ ...e, current: '' })); setSuccess(false); }}
              placeholder="Enter current password"
              error={errors.current}
            />

            <PasswordField
              label="New Password"
              value={next}
              onChange={(v) => { setNext(v); setErrors((e) => ({ ...e, next: '' })); setSuccess(false); }}
              placeholder="Enter new password"
              error={errors.next}
            />

            {/* Strength bar */}
            {next.length > 0 && (
              <div style={{ marginTop: -6 }}>
                <div style={{ display: 'flex', gap: 4, marginBottom: 5 }}>
                  {[1, 2, 3].map((lvl) => (
                    <div
                      key={lvl}
                      style={{
                        flex: 1, height: 3, borderRadius: 100,
                        background: lvl <= strength ? strengthColor[strength] : '#F3F2F0',
                        transition: 'background 0.2s',
                      }}
                    />
                  ))}
                </div>
                <span style={{ fontSize: 11, fontWeight: 700, color: strengthColor[strength] }}>
                  {strengthLabel[strength]}
                </span>
              </div>
            )}

            <PasswordField
              label="Confirm New Password"
              value={confirm}
              onChange={(v) => { setConfirm(v); setErrors((e) => ({ ...e, confirm: '' })); setSuccess(false); }}
              placeholder="Re-enter new password"
              error={errors.confirm}
            />

            {/* Submit button */}
            <button
              onClick={handleSubmit}
              disabled={loading}
              style={{
                width: '100%', padding: '15px',
                background: loading
                  ? '#FDBA74'
                  : 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
                border: 'none', borderRadius: '100px',
                fontSize: '15px', fontWeight: 800, color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
                boxShadow: loading ? 'none' : '0 6px 20px rgba(249,115,22,0.38)',
                letterSpacing: '-0.1px',
                marginTop: 4,
                transition: 'background 0.2s, box-shadow 0.2s',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              }}
            >
              {loading ? (
                <>
                  <svg
                    width="16" height="16" viewBox="0 0 16 16"
                    style={{ animation: 'spin 0.8s linear infinite' }}
                  >
                    <circle cx="8" cy="8" r="6" fill="none" stroke="white" strokeWidth="2" strokeDasharray="28" strokeDashoffset="8" strokeLinecap="round" />
                  </svg>
                  Updating…
                </>
              ) : (
                <>
                  <Lock size={15} strokeWidth={2.5} />
                  Change Password
                </>
              )}
            </button>
          </div>
        </div>

        {/* ── Tip card ── */}
        <div
          style={{
            background: '#FFF7ED', borderRadius: 16,
            border: '1.5px solid #FED7AA',
            padding: '14px 16px',
            display: 'flex', gap: 12, alignItems: 'flex-start',
          }}
        >
          <div
            style={{
              width: 32, height: 32, borderRadius: 10,
              background: 'white', border: '1.5px solid #FED7AA',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <Shield size={15} strokeWidth={2} style={{ color: '#F97316' }} />
          </div>
          <div>
            <p style={{ fontSize: 13, fontWeight: 700, color: '#9A3412', margin: '0 0 3px' }}>
              Keep your account safe
            </p>
            <p style={{ fontSize: 12, color: '#C2410C', margin: 0, lineHeight: 1.5 }}>
              Use at least 8 characters with a mix of uppercase, lowercase, numbers, and symbols.
            </p>
          </div>
        </div>

      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
