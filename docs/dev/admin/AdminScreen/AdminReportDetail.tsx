import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';
import {
  ArrowLeft, ExternalLink, CheckCircle2, XCircle,
  Info, Loader2, AlertTriangle, Clock, FileText,
  ShieldCheck, ChevronRight,
} from 'lucide-react';
import {
  useReports,
  formatDate,
  reasonLabel,
  REASON_COLORS,
  STATUS_STYLE,
  type ReportStatus,
} from './adminData';

// ── Sub-components ────────────────────────────────────────────────────────────
function SectionCard({
  title,
  badge,
  children,
}: {
  title: string;
  badge?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        background: 'white', borderRadius: '18px',
        border: '1px solid #E2E8F0',
        boxShadow: '0 1px 8px rgba(0,0,0,0.05)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex', alignItems: 'center', gap: '10px',
          padding: '16px 24px', borderBottom: '1px solid #F1F5F9',
          background: '#F8FAFC',
        }}
      >
        <span style={{ fontSize: '13px', fontWeight: 800, color: '#0F172A', letterSpacing: '0.1px' }}>
          {title}
        </span>
        {badge}
      </div>
      <div style={{ padding: '24px' }}>{children}</div>
    </div>
  );
}

function FieldRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: 'grid', gridTemplateColumns: '180px 1fr',
        gap: '12px', alignItems: 'flex-start',
        padding: '11px 0', borderBottom: '1px solid #F8FAFC',
      }}
    >
      <dt
        style={{
          fontSize: '12px', fontWeight: 700, color: '#94A3B8',
          letterSpacing: '0.4px', textTransform: 'uppercase', paddingTop: '1px',
        }}
      >
        {label}
      </dt>
      <dd style={{ fontSize: '14px', color: '#1E293B', fontWeight: 400, margin: 0 }}>
        {children}
      </dd>
    </div>
  );
}

function InfoBanner({
  type,
  message,
}: {
  type: 'success' | 'info' | 'warning';
  message: string;
}) {
  const styles = {
    success: { bg: '#F0FDF4', border: '#BBF7D0', color: '#15803D', icon: CheckCircle2 },
    info:    { bg: '#EFF6FF', border: '#BFDBFE', color: '#1D4ED8', icon: Info         },
    warning: { bg: '#FFFBEB', border: '#FDE68A', color: '#D97706', icon: AlertTriangle },
  }[type];
  const Icon = styles.icon;

  return (
    <div
      style={{
        display: 'flex', alignItems: 'flex-start', gap: '12px',
        background: styles.bg, border: `1px solid ${styles.border}`,
        borderRadius: '12px', padding: '14px 16px', marginTop: '20px',
      }}
    >
      <Icon size={17} strokeWidth={2.5} style={{ color: styles.color, flexShrink: 0, marginTop: '1px' }} />
      <p style={{ fontSize: '13px', color: styles.color, fontWeight: 500, lineHeight: 1.5, margin: 0 }}>
        {message}
      </p>
    </div>
  );
}

// ── Confirmation Dialog ───────────────────────────────────────────────────────
function ConfirmDialog({
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

  const title = isApprove
    ? 'Confirm: Approve This Report'
    : 'Confirm: Reject This Report';

  const bodyApprove = (
    <>
      You are marking this report as <strong>valid</strong>. The trust score deduction already applied to{' '}
      <strong>{reportedUserName}</strong> will remain permanently.
      <br /><br />
      <span style={{ color: '#DC2626' }}>This action cannot be undone.</span>
    </>
  );

  const bodyRejectWithPenalty = (
    <>
      You are dismissing this report as <strong>invalid</strong>. The trust score deducted from{' '}
      <strong>{reportedUserName}</strong> will be <strong>restored</strong>.
      <br /><br />
      <span style={{ color: '#DC2626' }}>This action cannot be undone.</span>
    </>
  );

  const bodyRejectNoPenalty = (
    <>
      You are dismissing this report. No trust score change was applied, so no reversal is needed.
      <br /><br />
      <span style={{ color: '#DC2626' }}>This action cannot be undone.</span>
    </>
  );

  const body = isApprove
    ? bodyApprove
    : hasPenalty
      ? bodyRejectWithPenalty
      : bodyRejectNoPenalty;

  const confirmLabel    = isApprove ? 'Confirm Approval' : 'Confirm Rejection';
  const confirmBg       = isApprove ? '#16A34A' : '#DC2626';
  const confirmShadow   = isApprove
    ? '0 6px 20px rgba(22,163,74,0.36)'
    : '0 6px 20px rgba(220,38,38,0.36)';

  return (
    // Backdrop
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 100,
        background: 'rgba(15,23,42,0.60)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '20px',
      }}
      onClick={onCancel}
    >
      {/* Dialog card */}
      <div
        style={{
          background: 'white', borderRadius: '22px',
          boxShadow: '0 24px 80px rgba(0,0,0,0.28)',
          padding: '32px', maxWidth: '480px', width: '100%',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Icon */}
        <div
          style={{
            width: '52px', height: '52px', borderRadius: '16px', marginBottom: '20px',
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
        <h2
          style={{
            fontSize: '18px', fontWeight: 800, color: '#0F172A',
            letterSpacing: '-0.3px', marginBottom: '12px',
          }}
        >
          {title}
        </h2>

        {/* Body */}
        <p
          style={{
            fontSize: '14px', color: '#475569', lineHeight: 1.65,
            marginBottom: '28px',
          }}
        >
          {body}
        </p>

        {/* Buttons */}
        <div style={{ display: 'flex', gap: '10px' }}>
          {/* Cancel */}
          <button
            onClick={onCancel}
            disabled={isProcessing}
            style={{
              flex: 1, padding: '13px',
              background: 'white', border: '1.5px solid #E2E8F0',
              borderRadius: '12px', cursor: isProcessing ? 'default' : 'pointer',
              fontSize: '14px', fontWeight: 600, color: '#64748B',
              opacity: isProcessing ? 0.5 : 1, transition: 'opacity 0.15s',
            }}
          >
            Cancel
          </button>

          {/* Confirm */}
          <button
            onClick={onConfirm}
            disabled={isProcessing}
            style={{
              flex: 2, padding: '13px',
              background: isProcessing ? '#94A3B8' : confirmBg,
              border: 'none', borderRadius: '12px',
              cursor: isProcessing ? 'default' : 'pointer',
              fontSize: '14px', fontWeight: 700, color: 'white',
              boxShadow: isProcessing ? 'none' : confirmShadow,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
              transition: 'all 0.15s',
            }}
          >
            {isProcessing && (
              <Loader2 size={16} strokeWidth={2.5} className="animate-spin" />
            )}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────
export function AdminReportDetail() {
  const { id }   = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { reports, updateReport } = useReports();

  const report = reports.find((r) => r.id === id);

  const [adminNote,    setAdminNote]    = useState(report?.adminNote ?? '');
  const [dialogType,   setDialogType]   = useState<'approve' | 'reject' | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  // ── Not found ──────────────────────────────────────────────────────────────
  if (!report) {
    return (
      <div style={{ padding: '40px 48px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', paddingTop: '80px' }}>
        <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <FileText size={28} strokeWidth={1.5} style={{ color: '#94A3B8' }} />
        </div>
        <p style={{ fontSize: '16px', fontWeight: 700, color: '#1E293B' }}>Report not found</p>
        <button
          onClick={() => navigate('/admin/reports')}
          style={{ fontSize: '14px', color: '#F97316', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer' }}
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
  const MAX_NOTE    = 500;

  // ── Action handler ─────────────────────────────────────────────────────────
  const handleConfirm = () => {
    if (!dialogType) return;
    setIsProcessing(true);

    setTimeout(() => {
      const now = new Date().toISOString();
      updateReport(report.id, {
        status:     dialogType === 'approve' ? 'approved' : 'rejected',
        resolvedAt: now,
        adminNote:  adminNote.trim() || undefined,
      });

      if (dialogType === 'approve') {
        toast.success('Report approved. Trust penalty stands.');
      } else {
        toast.success('Report rejected. Trust score restored.');
      }

      setIsProcessing(false);
      setDialogType(null);
    }, 1200);
  };

  const handleActionFailed = () => {
    toast.error('Something went wrong. Please try again.');
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      <div style={{ padding: '36px 48px', maxWidth: '900px' }}>

        {/* ── Breadcrumb + Back ────────────────────────────── */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px' }}>
          <button
            onClick={() => navigate('/admin/reports')}
            style={{
              display: 'flex', alignItems: 'center', gap: '6px',
              padding: '7px 14px', background: 'white',
              border: '1px solid #E2E8F0', borderRadius: '9px',
              fontSize: '13px', fontWeight: 600, color: '#475569', cursor: 'pointer',
            }}
          >
            <ArrowLeft size={14} strokeWidth={2.5} />
            Reports
          </button>
          <ChevronRight size={14} strokeWidth={2} style={{ color: '#CBD5E1' }} />
          <span style={{ fontSize: '13px', color: '#64748B', fontFamily: 'ui-monospace, monospace' }}>
            {report.id}
          </span>
        </div>

        {/* ── Page title row ───────────────────────────────── */}
        <div
          style={{
            display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
            gap: '16px', marginBottom: '28px', flexWrap: 'wrap',
          }}
        >
          <div>
            <h1 style={{ fontSize: '22px', fontWeight: 800, color: '#0F172A', letterSpacing: '-0.4px' }}>
              {report.id} — Report Review
            </h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '13px', color: '#94A3B8' }}>Filed {formatDate(report.filedAt)}</span>
              <span style={{ fontSize: '13px', color: '#CBD5E1' }}>·</span>
              <span
                style={{
                  background: reasonStyle.bg, borderRadius: '8px',
                  padding: '3px 10px', fontSize: '12px',
                  fontWeight: 600, color: reasonStyle.color,
                }}
              >
                {reasonLabel(report.reason)}
              </span>
            </div>
          </div>

          {/* Status badge (large) */}
          <div
            style={{
              display: 'flex', alignItems: 'center', gap: '7px',
              background: statusStyle.bg,
              border: `1.5px solid ${statusStyle.border}`,
              borderRadius: '14px', padding: '10px 18px',
            }}
          >
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: statusStyle.dot }} />
            <span style={{ fontSize: '13px', fontWeight: 800, color: statusStyle.color, letterSpacing: '0.5px' }}>
              {statusStyle.label}
            </span>
          </div>
        </div>

        {/* ── Section A: Evidence ──────────────────────────── */}
        <SectionCard
          title="A · Evidence"
          badge={
            <span
              style={{
                fontSize: '10px', fontWeight: 700, color: '#94A3B8',
                background: '#E2E8F0', borderRadius: '6px', padding: '2px 8px', letterSpacing: '0.5px',
              }}
            >
              READ ONLY
            </span>
          }
        >
          <dl style={{ margin: 0 }}>
            <FieldRow label="Report ID">
              <code style={{ fontSize: '13px', background: '#F1F5F9', borderRadius: '6px', padding: '2px 8px', color: '#1E293B', fontFamily: 'ui-monospace, monospace' }}>
                {report.id}
              </code>
            </FieldRow>

            <FieldRow label="Filed On">
              <span style={{ color: '#475569' }}>{formatDate(report.filedAt)}</span>
            </FieldRow>

            <FieldRow label="Reporter">
              <span style={{ fontWeight: 600, color: '#0F172A' }}>{report.reporterName}</span>
            </FieldRow>

            <FieldRow label="Reported User">
              <span style={{ fontWeight: 600, color: '#0F172A' }}>{report.reportedUserName}</span>
            </FieldRow>

            <FieldRow label="Reason">
              <span
                style={{
                  display: 'inline-block',
                  background: reasonStyle.bg, borderRadius: '8px',
                  padding: '4px 12px', fontSize: '13px',
                  fontWeight: 600, color: reasonStyle.color,
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
                    display: 'inline-flex', alignItems: 'center', gap: '5px',
                    color: '#2563EB', fontWeight: 500, fontSize: '13px',
                    textDecoration: 'none', wordBreak: 'break-all',
                  }}
                >
                  <span style={{ textDecoration: 'underline' }}>{report.evidenceLink}</span>
                  <ExternalLink size={13} strokeWidth={2} style={{ flexShrink: 0 }} />
                </a>
              ) : (
                <span style={{ color: '#94A3B8', fontStyle: 'italic' }}>No evidence provided</span>
              )}
            </FieldRow>

            <FieldRow label="Trust Score Impact">
              {hasPenalty ? (
                <span
                  style={{
                    display: 'inline-flex', alignItems: 'center', gap: '6px',
                    background: '#FEF2F2', borderRadius: '8px', padding: '4px 12px',
                    fontSize: '13px', fontWeight: 700, color: '#DC2626',
                  }}
                >
                  −{report.trustScorePenalty} points applied on submission
                </span>
              ) : (
                <span style={{ color: '#94A3B8' }}>No penalty applied</span>
              )}
            </FieldRow>
          </dl>
        </SectionCard>

        {/* Spacer */}
        <div style={{ height: '16px' }} />

        {/* ── Section B: Resolution ────────────────────────── */}
        <SectionCard title="B · Resolution">

          {/* ── PENDING: show form ──────────────────────────── */}
          {isPending && (
            <div>
              <p style={{ fontSize: '13px', color: '#64748B', marginBottom: '20px', lineHeight: 1.5 }}>
                Review the evidence above before making a decision. Both actions are irreversible.
              </p>

              {/* Note textarea */}
              <div style={{ marginBottom: '24px' }}>
                <label
                  style={{
                    display: 'block', fontSize: '13px', fontWeight: 700,
                    color: '#374151', marginBottom: '8px',
                  }}
                >
                  Resolution Note
                  <span style={{ fontWeight: 400, color: '#94A3B8', marginLeft: '6px' }}>(optional)</span>
                </label>
                <textarea
                  value={adminNote}
                  onChange={(e) => setAdminNote(e.target.value.slice(0, MAX_NOTE))}
                  placeholder="Add context or reasoning for your decision…"
                  rows={4}
                  style={{
                    width: '100%', boxSizing: 'border-box',
                    padding: '13px 16px', borderRadius: '12px',
                    border: '1.5px solid #E2E8F0',
                    background: '#F8FAFC',
                    fontSize: '13px', color: '#1E293B',
                    fontFamily: 'Inter, sans-serif', lineHeight: 1.55,
                    outline: 'none', resize: 'vertical',
                    transition: 'border-color 0.15s',
                  }}
                  onFocus={(e) => (e.currentTarget.style.borderColor = '#F97316')}
                  onBlur={(e)  => (e.currentTarget.style.borderColor = '#E2E8F0')}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
                  <span style={{ fontSize: '11px', color: adminNote.length > MAX_NOTE * 0.85 ? '#D97706' : '#94A3B8' }}>
                    {adminNote.length} / {MAX_NOTE}
                  </span>
                </div>
              </div>

              {/* Action buttons */}
              <div style={{ display: 'flex', gap: '12px' }}>
                <button
                  onClick={() => setDialogType('approve')}
                  style={{
                    flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                    padding: '14px', background: '#16A34A',
                    border: 'none', borderRadius: '12px', cursor: 'pointer',
                    fontSize: '14px', fontWeight: 700, color: 'white',
                    boxShadow: '0 6px 20px rgba(22,163,74,0.30)',
                    transition: 'opacity 0.15s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.9')}
                  onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
                >
                  <CheckCircle2 size={17} strokeWidth={2.5} />
                  Approve Report
                </button>

                <button
                  onClick={() => setDialogType('reject')}
                  style={{
                    flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                    padding: '14px', background: 'white',
                    border: '1.5px solid #FECACA', borderRadius: '12px', cursor: 'pointer',
                    fontSize: '14px', fontWeight: 700, color: '#DC2626',
                    transition: 'all 0.15s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = '#FEF2F2';
                    e.currentTarget.style.borderColor = '#DC2626';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = 'white';
                    e.currentTarget.style.borderColor = '#FECACA';
                  }}
                >
                  <XCircle size={17} strokeWidth={2.5} />
                  Reject Report
                </button>
              </div>
            </div>
          )}

          {/* ── APPROVED: read-only summary ─────────────────── */}
          {isApproved && (
            <div>
              <dl style={{ margin: 0 }}>
                <FieldRow label="Decision">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '7px', fontSize: '14px', fontWeight: 700, color: '#15803D' }}>
                    <CheckCircle2 size={17} strokeWidth={2.5} />
                    Approved
                  </span>
                </FieldRow>
                <FieldRow label="Decided On">
                  <span style={{ color: '#475569' }}>
                    {report.resolvedAt ? formatDate(report.resolvedAt) : '—'}
                  </span>
                </FieldRow>
                <FieldRow label="Note">
                  {report.adminNote
                    ? <span style={{ color: '#374151', lineHeight: 1.6 }}>{report.adminNote}</span>
                    : <span style={{ color: '#94A3B8', fontStyle: 'italic' }}>No note added</span>
                  }
                </FieldRow>
              </dl>
              <InfoBanner
                type="success"
                message="The trust penalty remains in effect. No further action is required."
              />
            </div>
          )}

          {/* ── REJECTED: read-only summary ─────────────────── */}
          {isRejected && (
            <div>
              <dl style={{ margin: 0 }}>
                <FieldRow label="Decision">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '7px', fontSize: '14px', fontWeight: 700, color: '#DC2626' }}>
                    <XCircle size={17} strokeWidth={2.5} />
                    Rejected
                  </span>
                </FieldRow>
                <FieldRow label="Decided On">
                  <span style={{ color: '#475569' }}>
                    {report.resolvedAt ? formatDate(report.resolvedAt) : '—'}
                  </span>
                </FieldRow>
                <FieldRow label="Note">
                  {report.adminNote
                    ? <span style={{ color: '#374151', lineHeight: 1.6 }}>{report.adminNote}</span>
                    : <span style={{ color: '#94A3B8', fontStyle: 'italic' }}>No note added</span>
                  }
                </FieldRow>
              </dl>
              <InfoBanner
                type={hasPenalty ? 'warning' : 'info'}
                message={
                  hasPenalty
                    ? 'The trust penalty has been reversed. The reported user\'s score has been restored.'
                    : 'No trust adjustment was needed for this report.'
                }
              />
            </div>
          )}
        </SectionCard>

        {/* Bottom spacing */}
        <div style={{ height: '48px' }} />
      </div>

      {/* ── Confirmation Dialog ──────────────────────────────── */}
      {dialogType && (
        <ConfirmDialog
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
