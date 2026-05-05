import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ArrowLeft, Search, ChevronRight,
  Clock, CheckCircle2, XCircle, FileText, X,
} from 'lucide-react';
import {
  useReports, formatDate, reasonLabel,
  REASON_COLORS, STATUS_STYLE,
  type ReportStatus,
} from './adminData';

type FilterTab = 'all' | 'pending' | 'resolved';

export function MobileAdminReportsList() {
  const navigate = useNavigate();
  const { reports } = useReports();

  const [activeTab,   setActiveTab]   = useState<FilterTab>('all');
  const [search,      setSearch]      = useState('');
  const [showSearch,  setShowSearch]  = useState(false);

  const total    = reports.length;
  const pending  = reports.filter((r) => r.status === 'pending').length;
  const approved = reports.filter((r) => r.status === 'approved').length;
  const rejected = reports.filter((r) => r.status === 'rejected').length;

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
    { label: 'Total',    value: total,    Icon: FileText,    bg: '#F8FAFC', border: '#E2E8F0', color: '#1E293B', iconBg: '#E2E8F0' },
    { label: 'Pending',  value: pending,  Icon: Clock,       bg: '#FFFBEB', border: '#FDE68A', color: '#D97706', iconBg: '#FEF3C7' },
    { label: 'Approved', value: approved, Icon: CheckCircle2,bg: '#F0FDF4', border: '#BBF7D0', color: '#16A34A', iconBg: '#DCFCE7' },
    { label: 'Rejected', value: rejected, Icon: XCircle,     bg: '#FEF2F2', border: '#FECACA', color: '#DC2626', iconBg: '#FEE2E2' },
  ];

  const tabs: { key: FilterTab; label: string; count: number }[] = [
    { key: 'all',      label: 'All',      count: total             },
    { key: 'pending',  label: 'Pending',  count: pending           },
    { key: 'resolved', label: 'Resolved', count: approved + rejected },
  ];

  return (
    <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5', overflowY: 'auto' }}>

      {/* ── Header ── */}
      <div
        style={{
          padding: '56px 20px 12px',
          background: '#FEF9F5',
          position: 'sticky', top: 0, zIndex: 10,
          borderBottom: showSearch ? '1px solid #F3F2F0' : 'none',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <button
            onClick={() => navigate('/profile')}
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

          <div style={{ textAlign: 'center' }}>
            <h1 style={{ fontSize: 18, fontWeight: 800, color: '#1C1917', letterSpacing: '-0.3px' }}>
              Reports
            </h1>
            {pending > 0 && (
              <span
                style={{
                  display: 'inline-block',
                  background: '#F97316', color: 'white',
                  borderRadius: 20, padding: '1px 10px',
                  fontSize: 10, fontWeight: 800, letterSpacing: '0.3px',
                  marginTop: 2,
                }}
              >
                {pending} PENDING
              </span>
            )}
          </div>

          <button
            onClick={() => { setShowSearch(!showSearch); if (showSearch) setSearch(''); }}
            style={{
              width: 40, height: 40, borderRadius: 12,
              background: showSearch ? '#FFF7ED' : 'white',
              border: `1.5px solid ${showSearch ? '#FED7AA' : '#F3F2F0'}`,
              boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer',
            }}
          >
            {showSearch
              ? <X size={17} strokeWidth={2.5} style={{ color: '#F97316' }} />
              : <Search size={17} strokeWidth={2} style={{ color: '#44403C' }} />
            }
          </button>
        </div>

        {/* Search bar */}
        {showSearch && (
          <div
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              background: 'white', border: '1.5px solid #F3F2F0',
              borderRadius: 14, padding: '0 14px', marginTop: 12,
            }}
          >
            <Search size={15} strokeWidth={2} style={{ color: '#A8A29E', flexShrink: 0 }} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search ID, name, reason…"
              autoFocus
              style={{
                flex: 1, border: 'none', outline: 'none',
                background: 'transparent', fontSize: 14, color: '#1C1917',
                padding: '12px 0', fontFamily: 'Inter, sans-serif',
              }}
            />
          </div>
        )}
      </div>

      <div style={{ padding: '16px 20px 100px', flex: 1 }}>

        {/* ── Stats 2×2 grid ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 16 }}>
          {stats.map((s) => (
            <div
              key={s.label}
              style={{
                background: s.bg, border: `1.5px solid ${s.border}`,
                borderRadius: 16, padding: '14px 16px',
                display: 'flex', alignItems: 'center', gap: 12,
              }}
            >
              <div
                style={{
                  width: 36, height: 36, borderRadius: 11,
                  background: s.iconBg, flexShrink: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}
              >
                <s.Icon size={17} strokeWidth={2} style={{ color: s.color }} />
              </div>
              <div>
                <div style={{ fontSize: 22, fontWeight: 800, color: s.color, lineHeight: 1 }}>
                  {s.value}
                </div>
                <div style={{ fontSize: 11, color: s.color, opacity: 0.65, fontWeight: 500, marginTop: 3 }}>
                  {s.label}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* ── Filter tabs ── */}
        <div
          style={{
            display: 'flex', gap: 6, marginBottom: 14,
            overflowX: 'auto', paddingBottom: 2,
          }}
        >
          {tabs.map((tab) => {
            const active = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  padding: '8px 16px', borderRadius: 100, flexShrink: 0,
                  border: `1.5px solid ${active ? '#F97316' : '#F3F2F0'}`,
                  background: active ? '#FFF7ED' : 'white',
                  cursor: 'pointer', fontSize: 13,
                  fontWeight: active ? 700 : 500,
                  color: active ? '#F97316' : '#78716C',
                }}
              >
                {tab.label}
                <span
                  style={{
                    background: active ? '#F97316' : '#F3F2F0',
                    color: active ? 'white' : '#78716C',
                    borderRadius: 20, padding: '1px 7px',
                    fontSize: 11, fontWeight: 700,
                  }}
                >
                  {tab.count}
                </span>
              </button>
            );
          })}
        </div>

        {/* ── Report cards ── */}
        {filtered.length === 0 ? (
          <div
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center',
              padding: '60px 20px', gap: 12,
            }}
          >
            <div
              style={{
                width: 56, height: 56, borderRadius: 16,
                background: '#F3F2F0',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <FileText size={24} strokeWidth={1.5} style={{ color: '#A8A29E' }} />
            </div>
            <p style={{ fontSize: 15, fontWeight: 700, color: '#1C1917', margin: 0 }}>No reports found</p>
            <p style={{ fontSize: 13, color: '#A8A29E', textAlign: 'center', margin: 0 }}>
              {search ? 'Try clearing your search.' : 'No reports match this filter.'}
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {filtered.map((report) => {
              const s  = STATUS_STYLE[report.status];
              const rc = REASON_COLORS[report.reason];
              const isPending = report.status === 'pending';

              return (
                <button
                  key={report.id}
                  onClick={() => navigate(`/admin/reports/${report.id}`)}
                  style={{
                    background: 'white',
                    border: `1.5px solid ${isPending ? '#FED7AA' : '#F3F2F0'}`,
                    borderRadius: 18, padding: 16, cursor: 'pointer',
                    textAlign: 'left', display: 'block', width: '100%',
                    boxShadow: isPending
                      ? '0 4px 16px rgba(249,115,22,0.09)'
                      : '0 2px 8px rgba(0,0,0,0.04)',
                  }}
                >
                  {/* Top row: ID + status badge */}
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 8 }}>
                    <div>
                      <code style={{ fontSize: 11, color: '#A8A29E', fontFamily: 'ui-monospace, monospace' }}>
                        {report.id}
                      </code>
                      <div style={{ fontSize: 15, fontWeight: 700, color: '#1C1917', marginTop: 2 }}>
                        {report.reportedUserName}
                      </div>
                    </div>
                    <span
                      style={{
                        display: 'inline-flex', alignItems: 'center', gap: 4,
                        background: s.bg, border: `1px solid ${s.border}`,
                        borderRadius: 20, padding: '3px 10px', flexShrink: 0, marginLeft: 8,
                        fontSize: 10, fontWeight: 800, color: s.color, letterSpacing: '0.5px',
                      }}
                    >
                      <span style={{ width: 5, height: 5, borderRadius: '50%', background: s.dot, display: 'inline-block' }} />
                      {s.label}
                    </span>
                  </div>

                  {/* Reason + reporter */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
                    <span
                      style={{
                        background: rc.bg, borderRadius: 8,
                        padding: '3px 10px', fontSize: 12, fontWeight: 600, color: rc.color,
                      }}
                    >
                      {reasonLabel(report.reason)}
                    </span>
                    <span style={{ fontSize: 12, color: '#A8A29E' }}>
                      by {report.reporterName}
                    </span>
                  </div>

                  {/* Bottom row: date + action */}
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 11, color: '#A8A29E' }}>
                      {formatDate(report.filedAt)}
                    </span>
                    {isPending ? (
                      <span
                        style={{
                          display: 'inline-flex', alignItems: 'center', gap: 4,
                          background: 'linear-gradient(135deg, #F97316, #FB923C)',
                          borderRadius: 10, padding: '6px 14px',
                          fontSize: 12, fontWeight: 700, color: 'white',
                          boxShadow: '0 3px 10px rgba(249,115,22,0.30)',
                        }}
                      >
                        Review <ChevronRight size={13} strokeWidth={2.5} />
                      </span>
                    ) : (
                      <ChevronRight size={16} strokeWidth={2} style={{ color: '#D1C5BF' }} />
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
