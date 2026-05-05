import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';
import {
  ArrowLeft, ExternalLink, CheckCircle2, XCircle,
  Loader2, AlertTriangle, Info, FileText,
  ShieldAlert, CalendarDays, User, Flag,
  ChevronRight,
} from 'lucide-react';
import {
  useReports, formatDate, reasonLabel,
  REASON_COLORS, STATUS_STYLE,
} from './adminData';

// ── Confirmation bottom sheet ─────────────────────────────────────────────────
function ConfirmSheet({
  type,
  reportedUserName,
  hasPenalty,
  isProcessing,
  onConfirm,
  onCancel,
}: {
  type: 'approve' | 'reject';
  reportedUserName: string;
  hasPenalty: boolean;
  isProcessing: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const isApprove = type === 'approve';

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 50,
        background: 'rgba(15,23,42,0.55)', backdropFilter: 'blur(4px)',
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
      }}
      onClick={onCancel}
    >
      <div
        style={{
          background: 'white',
          borderRadius: '28px 28px 0 0',
          padding: '28px 24px 40px',
          boxShadow: '0 -8px 40px rgba(0,0,0,0.18)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Handle */}
        <div
          style={{
            width: 40, height: 4, borderRadius: 100,
            background: '#E7E5E4', margin: '0 auto 24px',
          }}
        />

        {/* Icon */}
        <div
          style={{
            width: 52, height: 52, borderRadius: 16, marginBottom: 16,
            background: isApprove ? '#F0FDF4' : '#FEF2F2',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          {isApprove
            ? <CheckCircle2 size={26} strokeWidth={2} style={{ color: '#16A34A' }} />
            : <XCircle      size={26} strokeWidth={2} style={{ color: '#DC2626' }} />
          }
        </div>

        {/* Title */}
        <h2 style={{ fontSize: 18, fontWeight: 800, color: '#0F172A', letterSpacing: '-0.3px', marginBottom: 10 }}>
          {isApprove ? 'Confirm Approval' : 'Confirm Rejection'}
        </h2>

        {/* Body */}
        <p style={{ fontSize: 14, color: '#475569', lineHeight: 1.65, marginBottom: 28 }}>
          {isApprove ? (
            <>You are marking this report as <strong>valid</strong>. The trust penalty applied to <strong>{reportedUserName}</strong> will remain permanently. <span style={{ color: '#DC2626' }}>This cannot be undone.</span></>
          ) : hasPenalty ? (
            <>You are dismissing this report as <strong>invalid</strong>. The trust score deducted from <strong>{reportedUserName}</strong> will be <strong>restored</strong>. <span style={{ color: '#DC2626' }}>This cannot be undone.</span></>
          ) : (
            <>You are dismissing this report. No trust score change was applied. <span style={{ color: '#DC2626' }}>This cannot be undone.</span></>
          )}
        </p>

        {/* Buttons */}
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={onCancel}
            disabled={isProcessing}
            style={{
              flex: 1, padding: '14px',
              background: 'white', border: '1.5px solid #E2E8F0',
              borderRadius: 16, cursor: isProcessing ? 'default' : 'pointer',
              fontSize: 14, fontWeight: 600, color: '#64748B',
              opacity: isProcessing ? 0.5 : 1,
            }}
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={isProcessing}
            style={{
              flex: 2, padding: '14px',
              background: isProcessing ? '#94A3B8' : (isApprove ? '#16A34A' : '#DC2626'),
              border: 'none', borderRadius: 16,
              cursor: isProcessing ? 'default' : 'pointer',
              fontSize: 14, fontWeight: 700, color: 'white',
              boxShadow: isProcessing ? 'none' : (isApprove
                ? '0 6px 20px rgba(22,163,74,0.36)'
                : '0 6px 20px rgba(220,38,38,0.36)'),
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            }}
          >
            {isProcessing && <Loader2 size={16} strokeWidth={2.5} className="animate-spin" />}
            {isApprove ? 'Confirm Approval' : 'Confirm Rejection'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Info banner ───────────────────────────────────────────────────────────────
function InfoBanner({ type, message }: { type: 'success' | 'info' | 'warning'; message: string }) {
  const styles = {
    success: { bg: '#F0FDF4', border: '#BBF7D0', color: '#15803D', Icon: CheckCircle2 },
    info:    { bg: '#EFF6FF', border: '#BFDBFE', color: '#1D4ED8', Icon: Info         },
    warning: { bg: '#FFFBEB', border: '#FDE68A', color: '#D97706', Icon: AlertTriangle },
  }[type];

  return (
    <div
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 12,
        background: styles.bg, border: `1px solid ${styles.border}`,
        borderRadius: 14, padding: '14px 16px', marginTop: 16,
      }}
    >
      <styles.Icon size={17} strokeWidth={2.5} style={{ color: styles.color, flexShrink: 0, marginTop: 1 }} />
      <p style={{ fontSize: 13, color: styles.color, fontWeight: 500, lineHeight: 1.5, margin: 0 }}>
        {message}
      </p>
    </div>
  );
}

// ── Field row (mobile: vertical stack) ───────────────────────────────────────
function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ paddingBottom: 14, marginBottom: 14, borderBottom: '1px solid #F3F2F0' }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase', marginBottom: 5 }}>
        {label}
      </div>
      <div style={{ fontSize: 14, color: '#1C1917' }}>
        {children}
      </div>
    </div>
  );
}

// ── Card wrapper ──────────────────────────────────────────────────────────────
function Card({ title, badge, children }: { title: string; badge?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div
      style={{
        background: 'white', borderRadius: 20,
        border: '1.5px solid #F3F2F0',
        boxShadow: '0 2px 12px rgba(0,0,0,0.05)',
        overflow: 'hidden', marginBottom: 12,
      }}
    >
      <div
        style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '14px 18px', borderBottom: '1px solid #F3F2F0',
          background: '#FAFAF9',
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 800, color: '#1C1917', flex: 1 }}>{title}</span>
        {badge}
      </div>
      <div style={{ padding: '18px 18px 4px' }}>{children}</div>
    </div>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────
export function MobileAdminReportDetail() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { reports, updateReport } = useReports();

  const report = reports.find((r) => r.id === id);

  const [adminNote,    setAdminNote]    = useState(report?.adminNote ?? '');
  const [dialogType,   setDialogType]   = useState<'approve' | 'reject' | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  // ── Not found ───────────────────────────────────────────────────────────────
  if (!report) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center gap-4" style={{ background: '#FEF9F5' }}>
        <div style={{ width: 60, height: 60, borderRadius: 18, background: '#F3F2F0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <FileText size={26} strokeWidth={1.5} style={{ color: '#A8A29E' }} />
        </div>
        <p style={{ fontSize: 16, fontWeight: 700, color: '#1C1917' }}>Report not found</p>
        <button
          onClick={() => navigate('/admin/reports')}
          style={{ fontSize: 14, color: '#F97316', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer' }}
        >
          ← Back to Reports
        </button>
      </div>
    );
  }

  const isPending  = report.status === 'pending';
  const isApproved = report.status === 'approved';
  const isRejected = report.status === 'rejected';
  const hasPenalty = report.trustScorePenalty > 0;
  const statusStyle = STATUS_STYLE[report.status];
  const reasonStyle = REASON_COLORS[report.reason];
  const MAX_NOTE = 500;

  const handleConfirm = () => {
    if (!dialogType) return;
    setIsProcessing(true);
    setTimeout(() => {
      updateReport(report.id, {
        status:     dialogType === 'approve' ? 'approved' : 'rejected',
        resolvedAt: new Date().toISOString(),
        adminNote:  adminNote.trim() || undefined,
      });
      toast.success(
        dialogType === 'approve'
          ? 'Report approved. Trust penalty stands.'
          : 'Report rejected. Trust score restored.'
      );
      setIsProcessing(false);
      setDialogType(null);
    }, 1200);
  };

  return (
    <>
      <div className="w-full h-full flex flex-col" style={{ background: '#FEF9F5', overflowY: 'auto' }}>

        {/* ── Header ── */}
        <div
          style={{
            padding: '56px 20px 14px',
            background: '#FEF9F5',
            position: 'sticky', top: 0, zIndex: 10,
            borderBottom: '1px solid #F3F2F0',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              onClick={() => navigate('/admin/reports')}
              style={{
                width: 40, height: 40, borderRadius: 12, flexShrink: 0,
                background: 'white', border: '1.5px solid #F3F2F0',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
              }}
            >
              <ArrowLeft size={18} strokeWidth={2.5} style={{ color: '#1C1917' }} />
            </button>

            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <code style={{ fontSize: 13, fontWeight: 700, color: '#1C1917', fontFamily: 'ui-monospace, monospace' }}>
                  {report.id}
                </code>
                <ChevronRight size={13} strokeWidth={2} style={{ color: '#D1C5BF' }} />
                <span style={{ fontSize: 13, color: '#78716C', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  Review
                </span>
              </div>
              <div style={{ fontSize: 11, color: '#A8A29E', marginTop: 2 }}>
                Filed {formatDate(report.filedAt)}
              </div>
            </div>

            {/* Status badge */}
            <div
              style={{
                display: 'flex', alignItems: 'center', gap: 5, flexShrink: 0,
                background: statusStyle.bg, border: `1.5px solid ${statusStyle.border}`,
                borderRadius: 20, padding: '5px 12px',
              }}
            >
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: statusStyle.dot }} />
              <span style={{ fontSize: 11, fontWeight: 800, color: statusStyle.color, letterSpacing: '0.5px' }}>
                {statusStyle.label}
              </span>
            </div>
          </div>
        </div>

        {/* ── Content ── */}
        <div style={{ padding: '16px 20px 48px' }}>

          {/* ── A · Evidence card ── */}
          <Card
            title="Evidence"
            badge={
              <span
                style={{
                  fontSize: 10, fontWeight: 700, color: '#A8A29E',
                  background: '#F3F2F0', borderRadius: 6, padding: '2px 8px', letterSpacing: '0.5px',
                }}
              >
                READ ONLY
              </span>
            }
          >
            <FieldRow label="Report ID">
              <code style={{ fontSize: 13, background: '#F1F5F9', borderRadius: 6, padding: '2px 8px', color: '#1C1917', fontFamily: 'ui-monospace, monospace' }}>
                {report.id}
              </code>
            </FieldRow>

            <FieldRow label="Reported User">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 28, height: 28, borderRadius: 9, background: '#FFF7ED', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <User size={14} strokeWidth={2} style={{ color: '#F97316' }} />
                </div>
                <span style={{ fontWeight: 700, color: '#1C1917' }}>{report.reportedUserName}</span>
              </div>
            </FieldRow>

            <FieldRow label="Reporter">
              <span style={{ fontWeight: 600 }}>{report.reporterName}</span>
            </FieldRow>

            <FieldRow label="Reason">
              <span
                style={{
                  display: 'inline-block',
                  background: reasonStyle.bg, borderRadius: 8,
                  padding: '4px 12px', fontSize: 13, fontWeight: 600, color: reasonStyle.color,
                }}
              >
                {reasonLabel(report.reason)}
              </span>
            </FieldRow>

            <FieldRow label="Evidence Link">
              {report.evidenceLink ? (
                <a
                  href={report.evidenceLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    display: 'inline-flex', alignItems: 'center', gap: 5,
                    color: '#2563EB', fontWeight: 500, fontSize: 13, textDecoration: 'none',
                  }}
                >
                  <span style={{ textDecoration: 'underline', wordBreak: 'break-all' }}>View Evidence</span>
                  <ExternalLink size={13} strokeWidth={2} style={{ flexShrink: 0 }} />
                </a>
              ) : (
                <span style={{ color: '#A8A29E', fontStyle: 'italic' }}>No evidence provided</span>
              )}
            </FieldRow>

            <div style={{ paddingBottom: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase', marginBottom: 5 }}>
                Trust Score Impact
              </div>
              {hasPenalty ? (
                <span
                  style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    background: '#FEF2F2', borderRadius: 8, padding: '5px 12px',
                    fontSize: 13, fontWeight: 700, color: '#DC2626',
                  }}
                >
                  <ShieldAlert size={14} strokeWidth={2.5} />
                  −{report.trustScorePenalty} pts applied on submission
                </span>
              ) : (
                <span style={{ color: '#A8A29E', fontSize: 13 }}>No penalty applied</span>
              )}
            </div>
          </Card>

          {/* ── B · Resolution card ── */}
          <Card title="Resolution">

            {/* PENDING: show form */}
            {isPending && (
              <div>
                <p style={{ fontSize: 13, color: '#78716C', marginBottom: 20, lineHeight: 1.6, marginTop: 0 }}>
                  Review the evidence above before deciding. Both actions are <strong>irreversible</strong>.
                </p>

                {/* Note textarea */}
                <div style={{ marginBottom: 20 }}>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 700, color: '#44403C', marginBottom: 8 }}>
                    Resolution Note
                    <span style={{ fontWeight: 400, color: '#A8A29E', marginLeft: 6 }}>(optional)</span>
                  </label>
                  <textarea
                    value={adminNote}
                    onChange={(e) => setAdminNote(e.target.value.slice(0, MAX_NOTE))}
                    placeholder="Add context or reasoning for your decision…"
                    rows={4}
                    style={{
                      width: '100%', boxSizing: 'border-box',
                      padding: '13px 14px', borderRadius: 14,
                      border: '1.5px solid #F3F2F0',
                      background: '#FAFAF9',
                      fontSize: 14, color: '#1C1917',
                      fontFamily: 'Inter, sans-serif', lineHeight: 1.55,
                      outline: 'none', resize: 'vertical',
                    }}
                    onFocus={(e) => (e.currentTarget.style.borderColor = '#F97316')}
                    onBlur={(e)  => (e.currentTarget.style.borderColor = '#F3F2F0')}
                  />
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
                    <span style={{ fontSize: 11, color: adminNote.length > MAX_NOTE * 0.85 ? '#D97706' : '#A8A29E' }}>
                      {adminNote.length} / {MAX_NOTE}
                    </span>
                  </div>
                </div>

                {/* Action buttons */}
                <div style={{ display: 'flex', gap: 10, paddingBottom: 4 }}>
                  <button
                    onClick={() => setDialogType('approve')}
                    style={{
                      flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                      padding: '14px', background: '#16A34A',
                      border: 'none', borderRadius: 16, cursor: 'pointer',
                      fontSize: 14, fontWeight: 700, color: 'white',
                      boxShadow: '0 6px 20px rgba(22,163,74,0.30)',
                    }}
                  >
                    <CheckCircle2 size={16} strokeWidth={2.5} />
                    Approve
                  </button>
                  <button
                    onClick={() => setDialogType('reject')}
                    style={{
                      flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                      padding: '14px',
                      background: 'white', border: '1.5px solid #FECACA',
                      borderRadius: 16, cursor: 'pointer',
                      fontSize: 14, fontWeight: 700, color: '#DC2626',
                    }}
                  >
                    <XCircle size={16} strokeWidth={2.5} />
                    Reject
                  </button>
                </div>
              </div>
            )}

            {/* APPROVED: read-only */}
            {isApproved && (
              <div>
                <FieldRow label="Decision">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 700, color: '#15803D' }}>
                    <CheckCircle2 size={16} strokeWidth={2.5} />
                    Approved
                  </span>
                </FieldRow>
                <FieldRow label="Decided On">
                  <span style={{ color: '#44403C' }}>{report.resolvedAt ? formatDate(report.resolvedAt) : '—'}</span>
                </FieldRow>
                <div style={{ paddingBottom: 4 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase', marginBottom: 5 }}>Note</div>
                  {report.adminNote
                    ? <p style={{ fontSize: 14, color: '#1C1917', lineHeight: 1.6, margin: 0 }}>{report.adminNote}</p>
                    : <span style={{ fontSize: 14, color: '#A8A29E', fontStyle: 'italic' }}>No note added</span>
                  }
                </div>
                <InfoBanner type="success" message="The trust penalty remains in effect. No further action is required." />
              </div>
            )}

            {/* REJECTED: read-only */}
            {isRejected && (
              <div>
                <FieldRow label="Decision">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 700, color: '#DC2626' }}>
                    <XCircle size={16} strokeWidth={2.5} />
                    Rejected
                  </span>
                </FieldRow>
                <FieldRow label="Decided On">
                  <span style={{ color: '#44403C' }}>{report.resolvedAt ? formatDate(report.resolvedAt) : '—'}</span>
                </FieldRow>
                <div style={{ paddingBottom: 4 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: '#A8A29E', letterSpacing: '0.5px', textTransform: 'uppercase', marginBottom: 5 }}>Note</div>
                  {report.adminNote
                    ? <p style={{ fontSize: 14, color: '#1C1917', lineHeight: 1.6, margin: 0 }}>{report.adminNote}</p>
                    : <span style={{ fontSize: 14, color: '#A8A29E', fontStyle: 'italic' }}>No note added</span>
                  }
                </div>
                <InfoBanner
                  type={hasPenalty ? 'warning' : 'info'}
                  message={
                    hasPenalty
                      ? "The trust penalty has been reversed. The reported user's score has been restored."
                      : "No trust adjustment was needed for this report."
                  }
                />
              </div>
            )}
          </Card>

          {/* Filed date footer */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, justifyContent: 'center', marginTop: 8 }}>
            <CalendarDays size={12} strokeWidth={2} style={{ color: '#A8A29E' }} />
            <span style={{ fontSize: 12, color: '#A8A29E' }}>Filed {formatDate(report.filedAt)}</span>
          </div>
        </div>
      </div>

      {/* ── Confirmation bottom sheet ── */}
      {dialogType && (
        <ConfirmSheet
          type={dialogType}
          reportedUserName={report.reportedUserName}
          hasPenalty={hasPenalty}
          isProcessing={isProcessing}
          onConfirm={handleConfirm}
          onCancel={() => !isProcessing && setDialogType(null)}
        />
      )}
    </>
  );
}
