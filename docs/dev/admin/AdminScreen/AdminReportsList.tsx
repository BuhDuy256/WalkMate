import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Search, ArrowRight, FileText, Clock,
  CheckCircle2, XCircle, ChevronRight, AlertTriangle,
  Flag,
} from 'lucide-react';
import {
  useReports,
  formatDate,
  reasonLabel,
  REASON_COLORS,
  STATUS_STYLE,
  type ReportStatus,
} from './adminData';

type FilterTab = 'all' | 'pending' | 'resolved';

// ── Status Badge ──────────────────────────────────────────────────────────────
function StatusBadge({ status }: { status: ReportStatus }) {
  const s = STATUS_STYLE[status];
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: '5px',
        background: s.bg, border: `1px solid ${s.border}`,
        borderRadius: '20px', padding: '3px 11px',
        fontSize: '10px', fontWeight: 800, color: s.color, letterSpacing: '0.5px',
        whiteSpace: 'nowrap',
      }}
    >
      <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: s.dot, display: 'inline-block', flexShrink: 0 }} />
      {s.label}
    </span>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────
export function AdminReportsList() {
  const navigate = useNavigate();
  const { reports } = useReports();

  const [activeTab, setActiveTab] = useState<FilterTab>('all');
  const [search,    setSearch]    = useState('');

  // Derived counts
  const total    = reports.length;
  const pending  = reports.filter((r) => r.status === 'pending').length;
  const approved = reports.filter((r) => r.status === 'approved').length;
  const rejected = reports.filter((r) => r.status === 'rejected').length;

  // Filtered list
  const filtered = reports.filter((r) => {
    const matchesTab =
      activeTab === 'all'     ? true :
      activeTab === 'pending' ? r.status === 'pending' :
      r.status !== 'pending';
    const q = search.trim().toLowerCase();
    const matchesSearch =
      !q ||
      r.id.toLowerCase().includes(q) ||
      r.reportedUserName.toLowerCase().includes(q) ||
      r.reporterName.toLowerCase().includes(q) ||
      reasonLabel(r.reason).toLowerCase().includes(q);
    return matchesTab && matchesSearch;
  });

  const stats = [
    {
      label: 'Total Reports',
      value: total,
      icon: FileText,
      bg: '#F8FAFC', border: '#E2E8F0', accent: '#1E293B', iconBg: '#E2E8F0',
    },
    {
      label: 'Pending Review',
      value: pending,
      icon: Clock,
      bg: '#FFFBEB', border: '#FDE68A', accent: '#D97706', iconBg: '#FEF3C7',
    },
    {
      label: 'Approved',
      value: approved,
      icon: CheckCircle2,
      bg: '#F0FDF4', border: '#BBF7D0', accent: '#15803D', iconBg: '#DCFCE7',
    },
    {
      label: 'Rejected',
      value: rejected,
      icon: XCircle,
      bg: '#FEF2F2', border: '#FECACA', accent: '#DC2626', iconBg: '#FEE2E2',
    },
  ];

  const tabs: { key: FilterTab; label: string; count: number }[] = [
    { key: 'all',      label: 'All Reports', count: total             },
    { key: 'pending',  label: 'Pending',     count: pending           },
    { key: 'resolved', label: 'Resolved',    count: approved + rejected },
  ];

  return (
    <div style={{ padding: '36px 48px', maxWidth: '1280px' }}>

      {/* ── Page header ─────────────────────────────────────── */}
      <div style={{ marginBottom: '28px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
          <div
            style={{
              width: '36px', height: '36px', borderRadius: '10px',
              background: 'linear-gradient(135deg, #F97316, #FB923C)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 4px 12px rgba(249,115,22,0.30)',
            }}
          >
            <Flag size={17} strokeWidth={2.5} style={{ color: 'white' }} />
          </div>
          <h1 style={{ fontSize: '24px', fontWeight: 800, color: '#0F172A', letterSpacing: '-0.4px' }}>
            Report Management
          </h1>
          {pending > 0 && (
            <span
              style={{
                background: '#F97316', color: 'white',
                borderRadius: '20px', padding: '2px 10px',
                fontSize: '12px', fontWeight: 700,
              }}
            >
              {pending} pending
            </span>
          )}
        </div>
        <p style={{ fontSize: '14px', color: '#64748B', lineHeight: 1.5 }}>
          Review and resolve user-submitted reports. All actions are permanent and cannot be undone.
        </p>
      </div>

      {/* ── Stats row ───────────────────────────────────────── */}
      <div
        style={{
          display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)',
          gap: '16px', marginBottom: '28px',
        }}
      >
        {stats.map((s) => (
          <div
            key={s.label}
            style={{
              background: s.bg, border: `1px solid ${s.border}`,
              borderRadius: '16px', padding: '20px',
              display: 'flex', alignItems: 'flex-start', gap: '14px',
            }}
          >
            <div
              style={{
                width: '42px', height: '42px', borderRadius: '12px',
                background: s.iconBg, flexShrink: 0,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <s.icon size={20} strokeWidth={2} style={{ color: s.accent }} />
            </div>
            <div>
              <div style={{ fontSize: '28px', fontWeight: 800, color: s.accent, letterSpacing: '-1px', lineHeight: 1 }}>
                {s.value}
              </div>
              <div style={{ fontSize: '12px', color: s.accent, opacity: 0.7, marginTop: '4px', fontWeight: 500 }}>
                {s.label}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* ── Table card ──────────────────────────────────────── */}
      <div
        style={{
          background: 'white', borderRadius: '18px',
          border: '1px solid #E2E8F0',
          boxShadow: '0 1px 8px rgba(0,0,0,0.05)',
          overflow: 'hidden',
        }}
      >
        {/* Toolbar: tabs + search */}
        <div
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '16px 20px', borderBottom: '1px solid #F1F5F9', gap: '16px',
            flexWrap: 'wrap',
          }}
        >
          {/* Segmented tabs */}
          <div
            style={{
              display: 'flex', gap: '3px', background: '#F1F5F9',
              borderRadius: '10px', padding: '3px',
            }}
          >
            {tabs.map((tab) => {
              const active = activeTab === tab.key;
              return (
                <button
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  style={{
                    padding: '7px 16px', borderRadius: '8px', border: 'none', cursor: 'pointer',
                    background: active ? 'white' : 'transparent',
                    boxShadow: active ? '0 1px 4px rgba(0,0,0,0.12)' : 'none',
                    fontSize: '13px', fontWeight: active ? 700 : 500,
                    color: active ? '#0F172A' : '#94A3B8',
                    display: 'flex', alignItems: 'center', gap: '7px',
                    transition: 'all 0.15s', whiteSpace: 'nowrap',
                  }}
                >
                  {tab.label}
                  <span
                    style={{
                      borderRadius: '20px', padding: '1px 8px', fontSize: '11px', fontWeight: 700,
                      background: active && tab.key === 'pending' ? '#FEF3C7' : '#E2E8F0',
                      color:      active && tab.key === 'pending' ? '#D97706'  : '#64748B',
                    }}
                  >
                    {tab.count}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Search */}
          <div
            style={{
              display: 'flex', alignItems: 'center', gap: '8px',
              background: '#F8FAFC', border: '1px solid #E2E8F0',
              borderRadius: '10px', padding: '0 13px', minWidth: '260px',
            }}
          >
            <Search size={14} strokeWidth={2} style={{ color: '#94A3B8', flexShrink: 0 }} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by ID, name, or reason…"
              style={{
                border: 'none', outline: 'none', background: 'transparent',
                fontSize: '13px', color: '#1E293B', padding: '9px 0',
                fontFamily: 'Inter, sans-serif', flex: 1,
              }}
            />
          </div>
        </div>

        {/* Table */}
        {filtered.length === 0 ? (
          <div
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center',
              padding: '72px 20px', gap: '12px',
            }}
          >
            <div
              style={{
                width: '64px', height: '64px', borderRadius: '20px',
                background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <FileText size={28} strokeWidth={1.5} style={{ color: '#94A3B8' }} />
            </div>
            <p style={{ fontSize: '16px', fontWeight: 700, color: '#1E293B' }}>No reports found.</p>
            <p style={{ fontSize: '13px', color: '#94A3B8' }}>
              {search ? 'Try clearing your search.' : 'No reports match this filter.'}
            </p>
          </div>
        ) : (
          <>
            {/* Column headers */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '168px 1fr 1fr 178px 130px 110px',
                padding: '10px 20px',
                background: '#F8FAFC',
                borderBottom: '1px solid #F1F5F9',
              }}
            >
              {['Date Filed', 'Reported User', 'Reporter', 'Reason', 'Status', 'Action'].map((col) => (
                <div
                  key={col}
                  style={{ fontSize: '11px', fontWeight: 700, color: '#94A3B8', letterSpacing: '0.5px' }}
                >
                  {col}
                </div>
              ))}
            </div>

            {/* Rows */}
            {filtered.map((report, idx) => {
              const reasonStyle = REASON_COLORS[report.reason];
              const isPending   = report.status === 'pending';

              return (
                <div
                  key={report.id}
                  onClick={() => navigate(`/admin/reports/${report.id}`)}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '168px 1fr 1fr 178px 130px 110px',
                    alignItems: 'center',
                    padding: '14px 20px',
                    borderBottom: idx < filtered.length - 1 ? '1px solid #F8FAFC' : 'none',
                    cursor: 'pointer',
                    background: 'white',
                    transition: 'background 0.12s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = '#F8FAFC')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'white')}
                >
                  {/* Date */}
                  <div style={{ fontSize: '12px', color: '#64748B', fontFamily: 'ui-monospace, monospace' }}>
                    {formatDate(report.filedAt)}
                  </div>

                  {/* Reported User */}
                  <div>
                    <div style={{ fontSize: '14px', fontWeight: 600, color: '#0F172A' }}>
                      {report.reportedUserName}
                    </div>
                    <div style={{ fontSize: '11px', color: '#94A3B8', marginTop: '1px' }}>
                      {report.id}
                    </div>
                  </div>

                  {/* Reporter */}
                  <div style={{ fontSize: '13px', color: '#475569' }}>
                    {report.reporterName}
                  </div>

                  {/* Reason */}
                  <div>
                    <span
                      style={{
                        display: 'inline-block',
                        background: reasonStyle.bg, borderRadius: '8px',
                        padding: '4px 10px', fontSize: '12px',
                        fontWeight: 600, color: reasonStyle.color,
                      }}
                    >
                      {reasonLabel(report.reason)}
                    </span>
                  </div>

                  {/* Status */}
                  <div>
                    <StatusBadge status={report.status} />
                  </div>

                  {/* Action */}
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    {isPending ? (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/admin/reports/${report.id}`);
                        }}
                        style={{
                          display: 'flex', alignItems: 'center', gap: '5px',
                          padding: '7px 14px',
                          background: 'linear-gradient(135deg, #F97316, #FB923C)',
                          border: 'none', borderRadius: '8px', cursor: 'pointer',
                          fontSize: '12px', fontWeight: 700, color: 'white',
                          boxShadow: '0 3px 10px rgba(249,115,22,0.28)',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        Review <ArrowRight size={12} strokeWidth={2.5} />
                      </button>
                    ) : (
                      <ChevronRight size={16} strokeWidth={2} style={{ color: '#CBD5E1' }} />
                    )}
                  </div>
                </div>
              );
            })}
          </>
        )}
      </div>
    </div>
  );
}
