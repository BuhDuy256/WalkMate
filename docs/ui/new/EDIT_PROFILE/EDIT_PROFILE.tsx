import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, Camera, ChevronDown } from 'lucide-react';

const AVATAR_URL =
  'https://images.unsplash.com/photo-1761933808230-9a2e78956daa?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx5b3VuZyUyMGFzaWFuJTIwd29tYW4lMjBwb3J0cmFpdCUyMHNtaWxpbmd8ZW58MXx8fHwxNzc3NDMyODUzfDA&ixlib=rb-4.1.0&q=80&w=400';

const ALL_TAGS = [
  'Pet Friendly', 'Power Walking', 'Scenic & Quiet',
  'Strolling', 'Talkative', 'Early Bird', 'Night Walker', 'Fitness Focus',
];

// ── Gender — 2 options only ─────────────────────────────────────────────────
const GENDER_OPTIONS = ['Male', 'Female'];

// ── DOB data ────────────────────────────────────────────────────────────────
const DAYS   = Array.from({ length: 31 }, (_, i) => String(i + 1).padStart(2, '0'));
const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const YEARS  = Array.from({ length: 75 }, (_, i) => String(2024 - i)); // 2024 → 1950

// ── DOB Segment Dropdown ────────────────────────────────────────────────────
type DobField = 'day' | 'month' | 'year';

function DobSegment({
  label,
  value,
  items,
  isOpen,
  onToggle,
  onSelect,
}: {
  label: string;
  value: string;
  items: string[];
  isOpen: boolean;
  onToggle: () => void;
  onSelect: (item: string) => void;
}) {
  const listRef = useRef<HTMLDivElement>(null);

  // Scroll selected item into view when dropdown opens
  useEffect(() => {
    if (isOpen && listRef.current) {
      const idx = items.indexOf(value);
      if (idx >= 0) {
        listRef.current.scrollTop = Math.max(0, idx * 44 - 44);
      }
    }
  }, [isOpen]);

  return (
    <div style={{ flex: 1, position: 'relative' }}>
      {/* Tap zone */}
      <button
        onClick={onToggle}
        style={{
          width: '100%',
          padding: '12px 8px 10px',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '3px',
        }}
      >
        <span style={{ fontSize: '10px', fontWeight: 700, color: '#A8A29E', textTransform: 'uppercase', letterSpacing: '0.6px' }}>
          {label}
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          <span style={{ fontSize: '16px', fontWeight: 800, color: isOpen ? '#F97316' : '#1C1917', letterSpacing: '-0.2px' }}>
            {value}
          </span>
          <ChevronDown
            size={13}
            strokeWidth={2.5}
            style={{
              color: isOpen ? '#F97316' : '#A8A29E',
              transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.2s, color 0.2s',
              marginTop: '1px',
            }}
          />
        </div>
      </button>

      {/* Dropdown list */}
      {isOpen && (
        <div
          ref={listRef}
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: '50%',
            transform: 'translateX(-50%)',
            width: '110px',
            maxHeight: '200px',
            overflowY: 'auto',
            background: 'white',
            borderRadius: '16px',
            border: '1.5px solid #F3F2F0',
            boxShadow: '0 12px 36px rgba(0,0,0,0.14)',
            zIndex: 50,
            scrollbarWidth: 'none',
          }}
        >
          {items.map((item) => {
            const selected = item === value;
            return (
              <button
                key={item}
                onClick={() => onSelect(item)}
                style={{
                  width: '100%',
                  padding: '11px 14px',
                  background: selected ? '#FFF7ED' : 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: selected ? 800 : 500,
                  color: selected ? '#F97316' : '#44403C',
                  textAlign: 'center',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '6px',
                }}
              >
                {selected && (
                  <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#F97316', flexShrink: 0 }} />
                )}
                {item}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── Main Screen ─────────────────────────────────────────────────────────────
export function EditProfileScreen() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('Luân Trần');
  const [gender, setGender]     = useState('Male');
  const [bio, setBio]           = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>(['Pet Friendly', 'Power Walking']);
  const [saved, setSaved]       = useState(false);

  // ── DOB state ──────────────────────────────────────────────────────────
  const [dobDay,   setDobDay]   = useState('15');
  const [dobMonth, setDobMonth] = useState('Apr');
  const [dobYear,  setDobYear]  = useState('2005');
  const [openDob,  setOpenDob]  = useState<DobField | null>(null);

  const toggleDob = (field: DobField) =>
    setOpenDob((prev) => (prev === field ? null : field));

  const toggleTag = (tag: string) => {
    setSelectedTags((prev) =>
      prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]
    );
  };

  const handleSave = () => {
    setSaved(true);
    setTimeout(() => navigate('/profile'), 700);
  };

  return (
    <div
      className="w-full h-full flex flex-col"
      style={{ background: '#FEF9F5', overflowY: 'auto' }}
      onClick={() => setOpenDob(null)}   // close dropdown on outside tap
    >
      {/* ── Header ─────────────────────────────────────────────── */}
      <div
        className="flex items-center gap-3 px-5 pt-14 pb-5"
        style={{ background: '#FEF9F5', position: 'sticky', top: 0, zIndex: 20 }}
      >
        <button
          onClick={() => navigate('/profile')}
          style={{
            width: '40px', height: '40px', background: '#F5F5F4',
            borderRadius: '12px', border: 'none', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}
        >
          <ChevronLeft size={20} style={{ color: '#1C1917' }} strokeWidth={2.5} />
        </button>
        <h2 style={{ flex: 1, textAlign: 'center', fontSize: '18px', fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
          Edit Profile
        </h2>
        <div style={{ width: 40 }} />
      </div>

      {/* ── Avatar ─────────────────────────────────────────────── */}
      <div className="flex flex-col items-center mb-8">
        <div style={{ position: 'relative', display: 'inline-block' }}>
          <div style={{ width: '88px', height: '88px', borderRadius: '50%', overflow: 'hidden', border: '3px solid white', boxShadow: '0 6px 20px rgba(0,0,0,0.14)' }}>
            <img src={AVATAR_URL} alt="Avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </div>
          <button
            style={{
              position: 'absolute', bottom: 0, right: 0,
              width: '28px', height: '28px', background: '#F97316', borderRadius: '50%',
              border: '2.5px solid white', display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer', boxShadow: '0 2px 8px rgba(249,115,22,0.4)',
            }}
          >
            <Camera size={13} strokeWidth={2.5} style={{ color: 'white' }} />
          </button>
        </div>
      </div>

      {/* ── Form ───────────────────────────────────────────────── */}
      <div className="px-6 flex flex-col gap-6 pb-10">

        {/* Full Name */}
        <div>
          <label style={{ fontSize: '13px', fontWeight: 600, color: '#78716C', display: 'block', marginBottom: '8px' }}>
            Full Name
          </label>
          <input
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            style={{
              width: '100%', fontSize: '16px', fontWeight: 500, color: '#1C1917',
              background: 'transparent', border: 'none', borderBottom: '1.5px solid #D6D3D1',
              padding: '8px 0', outline: 'none', fontFamily: 'Inter, sans-serif',
            }}
          />
        </div>

        {/* ── Gender — Male / Female only ────────────────────── */}
        <div>
          <label style={{ fontSize: '13px', fontWeight: 600, color: '#78716C', display: 'block', marginBottom: '12px' }}>
            Gender
          </label>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
            {GENDER_OPTIONS.map((option) => {
              const active = gender === option;
              return (
                <button
                  key={option}
                  onClick={() => setGender(option)}
                  style={{
                    padding: '15px 12px',
                    borderRadius: '16px',
                    border: active ? '2px solid #F97316' : '1.5px solid #E7E5E4',
                    background: active ? '#FFF7ED' : 'white',
                    fontSize: '15px',
                    fontWeight: active ? 800 : 500,
                    color: active ? '#F97316' : '#44403C',
                    cursor: 'pointer',
                    transition: 'all 0.15s',
                    boxShadow: active ? '0 4px 16px rgba(249,115,22,0.20)' : '0 1px 4px rgba(0,0,0,0.05)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                  }}
                >
                  {/* Selected indicator dot */}
                  <div
                    style={{
                      width: '9px', height: '9px', borderRadius: '50%', flexShrink: 0,
                      background: active ? '#F97316' : '#E7E5E4',
                      boxShadow: active ? '0 0 0 3px #FED7AA' : 'none',
                      transition: 'all 0.15s',
                    }}
                  />
                  {option}
                </button>
              );
            })}
          </div>
        </div>

        {/* ── Date of Birth — 3-segment dropdown bar ─────────── */}
        <div onClick={(e) => e.stopPropagation()}>
          <label style={{ fontSize: '13px', fontWeight: 600, color: '#78716C', display: 'block', marginBottom: '12px' }}>
            Date of Birth
          </label>

          {/* Bar */}
          <div
            style={{
              background: 'white',
              borderRadius: '18px',
              border: openDob ? '2px solid #F97316' : '1.5px solid #E7E5E4',
              boxShadow: openDob
                ? '0 4px 20px rgba(249,115,22,0.14)'
                : '0 2px 8px rgba(0,0,0,0.05)',
              display: 'flex',
              alignItems: 'stretch',
              overflow: 'visible',
              transition: 'border-color 0.15s, box-shadow 0.15s',
              position: 'relative',
            }}
          >
            {/* Day */}
            <DobSegment
              label="Day"
              value={dobDay}
              items={DAYS}
              isOpen={openDob === 'day'}
              onToggle={() => toggleDob('day')}
              onSelect={(v) => { setDobDay(v); setOpenDob(null); }}
            />

            {/* Divider */}
            <div style={{ width: '1px', background: '#F3F2F0', alignSelf: 'stretch', margin: '10px 0' }} />

            {/* Month */}
            <DobSegment
              label="Month"
              value={dobMonth}
              items={MONTHS}
              isOpen={openDob === 'month'}
              onToggle={() => toggleDob('month')}
              onSelect={(v) => { setDobMonth(v); setOpenDob(null); }}
            />

            {/* Divider */}
            <div style={{ width: '1px', background: '#F3F2F0', alignSelf: 'stretch', margin: '10px 0' }} />

            {/* Year */}
            <DobSegment
              label="Year"
              value={dobYear}
              items={YEARS}
              isOpen={openDob === 'year'}
              onToggle={() => toggleDob('year')}
              onSelect={(v) => { setDobYear(v); setOpenDob(null); }}
            />
          </div>

          {/* Inline summary */}
          {openDob === null && (
            <p style={{ fontSize: '12px', color: '#A8A29E', marginTop: '6px', textAlign: 'center' }}>
              {dobDay} {dobMonth} {dobYear}
            </p>
          )}
        </div>

        {/* Bio */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#78716C' }}>Bio</label>
            <span style={{ fontSize: '12px', color: '#A8A29E' }}>{bio.length}/500</span>
          </div>
          <textarea
            value={bio}
            onChange={(e) => setBio(e.target.value.slice(0, 500))}
            placeholder="Tell others about yourself..."
            rows={3}
            style={{
              width: '100%', fontSize: '15px', fontWeight: 400, color: '#1C1917',
              background: 'transparent', border: 'none', borderBottom: '1.5px solid #D6D3D1',
              padding: '8px 0', outline: 'none', resize: 'none',
              fontFamily: 'Inter, sans-serif', lineHeight: 1.6,
            }}
          />
        </div>

        {/* Personality Tags */}
        <div>
          <label style={{ fontSize: '13px', fontWeight: 600, color: '#78716C', display: 'block', marginBottom: '12px' }}>
            Personality Tags
          </label>
          <div className="flex flex-wrap gap-2.5">
            {ALL_TAGS.map((tag) => {
              const active = selectedTags.includes(tag);
              return (
                <button
                  key={tag}
                  onClick={() => toggleTag(tag)}
                  style={{
                    padding: '8px 16px', borderRadius: '100px',
                    border: active ? '1.5px solid #F97316' : '1.5px solid #E7E5E4',
                    background: active ? '#F97316' : 'white',
                    fontSize: '13px', fontWeight: active ? 700 : 500,
                    color: active ? 'white' : '#44403C',
                    cursor: 'pointer', transition: 'all 0.18s',
                    boxShadow: active ? '0 4px 12px rgba(249,115,22,0.3)' : '0 1px 3px rgba(0,0,0,0.06)',
                  }}
                >
                  {tag}
                </button>
              );
            })}
          </div>
        </div>

        {/* Save */}
        <button
          onClick={handleSave}
          style={{
            width: '100%',
            background: saved
              ? 'linear-gradient(135deg, #22C55E 0%, #16A34A 100%)'
              : 'linear-gradient(135deg, #F97316 0%, #FB923C 100%)',
            border: 'none', borderRadius: '100px', padding: '17px',
            fontSize: '16px', fontWeight: 800, color: 'white',
            cursor: 'pointer',
            boxShadow: saved ? '0 8px 24px rgba(34,197,94,0.4)' : '0 8px 24px rgba(249,115,22,0.4)',
            transition: 'all 0.3s', letterSpacing: '-0.2px', marginTop: '8px',
          }}
        >
          {saved ? '✓ Saved!' : 'Save'}
        </button>
      </div>
    </div>
  );
}