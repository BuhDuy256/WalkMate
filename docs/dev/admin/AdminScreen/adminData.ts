import { createContext, useContext, useState, createElement, type ReactNode } from 'react';

// ── Types ─────────────────────────────────────────────────────────────────────
export type ReportReason = 'safety_concern' | 'misconduct' | 'emergency' | 'other';
export type ReportStatus = 'pending' | 'approved' | 'rejected';

export interface Report {
  id: string;
  reportedUserName: string;
  reporterName: string;
  reason: ReportReason;
  status: ReportStatus;
  evidenceLink?: string;
  trustScorePenalty: number; // 0 = no penalty (Emergency reports)
  filedAt: string;           // ISO string
  resolvedAt?: string;
  adminNote?: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
export function formatDate(iso: string): string {
  const d = new Date(iso);
  const dd = String(d.getDate()).padStart(2, '0');
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  const hh  = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${dd} ${months[d.getMonth()]} ${d.getFullYear()}, ${hh}:${min}`;
}

export function reasonLabel(r: ReportReason): string {
  switch (r) {
    case 'safety_concern': return 'Safety Concern';
    case 'misconduct':     return 'Partner Misconduct';
    case 'emergency':      return 'Emergency';
    case 'other':          return 'Other';
  }
}

export const REASON_COLORS: Record<ReportReason, { bg: string; color: string }> = {
  safety_concern: { bg: '#FFF7ED', color: '#C2410C' },
  misconduct:     { bg: '#FEF2F2', color: '#DC2626' },
  emergency:      { bg: '#FFF0F0', color: '#991B1B' },
  other:          { bg: '#F8FAFC', color: '#475569' },
};

export const STATUS_STYLE: Record<ReportStatus, { label: string; bg: string; color: string; border: string; dot: string }> = {
  pending:  { label: 'PENDING',  bg: '#FFFBEB', color: '#D97706', border: '#FDE68A', dot: '#F59E0B' },
  approved: { label: 'APPROVED', bg: '#F0FDF4', color: '#16A34A', border: '#BBF7D0', dot: '#22C55E' },
  rejected: { label: 'REJECTED', bg: '#FEF2F2', color: '#DC2626', border: '#FECACA', dot: '#EF4444' },
};

// ── Mock data ─────────────────────────────────────────────────────────────────
const SEED_REPORTS: Report[] = [
  {
    id: 'RPT-001',
    reportedUserName: 'Nguyen Van An',
    reporterName: 'Tran Thi Bich',
    reason: 'safety_concern',
    status: 'pending',
    evidenceLink: 'https://drive.google.com/file/d/1abc_evidence_photo_001/view',
    trustScorePenalty: 30,
    filedAt: '2025-05-02T09:23:00',
  },
  {
    id: 'RPT-002',
    reportedUserName: 'Le Van Cuong',
    reporterName: 'Hoang Duc Duy',
    reason: 'misconduct',
    status: 'pending',
    trustScorePenalty: 20,
    filedAt: '2025-05-01T14:05:00',
  },
  {
    id: 'RPT-003',
    reportedUserName: 'Pham Thi Lan',
    reporterName: 'Bui Van Minh',
    reason: 'safety_concern',
    status: 'approved',
    evidenceLink: 'https://photos.app.goo.gl/evidence_photo_003',
    trustScorePenalty: 30,
    filedAt: '2025-04-29T11:40:00',
    resolvedAt: '2025-04-30T08:15:00',
    adminNote: 'Evidence clearly shows inappropriate behavior during the walk session. Penalty confirmed and remains in effect.',
  },
  {
    id: 'RPT-004',
    reportedUserName: 'Dao Thi Giang',
    reporterName: 'Ly Van Hieu',
    reason: 'other',
    status: 'rejected',
    trustScorePenalty: 10,
    filedAt: '2025-04-28T16:55:00',
    resolvedAt: '2025-04-29T10:02:00',
    adminNote: 'Report lacks sufficient context. Unable to verify the claim based on provided information. Penalty reversed.',
  },
  {
    id: 'RPT-005',
    reportedUserName: 'Trinh Van Khanh',
    reporterName: 'Nguyen Thi Kim',
    reason: 'emergency',
    status: 'pending',
    trustScorePenalty: 0,
    filedAt: '2025-04-30T07:12:00',
    evidenceLink: 'https://photos.app.goo.gl/emergency_report_005',
  },
  {
    id: 'RPT-006',
    reportedUserName: 'Vo Van Long',
    reporterName: 'Tran Thi Mai',
    reason: 'misconduct',
    status: 'approved',
    trustScorePenalty: 20,
    filedAt: '2025-04-27T13:20:00',
    resolvedAt: '2025-04-27T18:45:00',
    adminNote: 'Second complaint against this user within 7 days. Penalty upheld.',
  },
  {
    id: 'RPT-007',
    reportedUserName: 'Vu Thi Ngoc',
    reporterName: 'Cao Van Phuc',
    reason: 'safety_concern',
    status: 'rejected',
    trustScorePenalty: 30,
    filedAt: '2025-04-25T10:30:00',
    resolvedAt: '2025-04-26T09:10:00',
    adminNote: 'No credible evidence submitted. Reporter\'s claim could not be substantiated. Penalty reversed.',
  },
  {
    id: 'RPT-008',
    reportedUserName: 'Dinh Thi Quynh',
    reporterName: 'Ngo Van Son',
    reason: 'other',
    status: 'pending',
    trustScorePenalty: 10,
    filedAt: '2025-05-01T20:44:00',
  },
];

// ── Context ───────────────────────────────────────────────────────────────────
interface ReportsCtxValue {
  reports: Report[];
  updateReport: (id: string, patch: Partial<Report>) => void;
}

export const ReportsContext = createContext<ReportsCtxValue | null>(null);

export function ReportsProvider({ children }: { children: ReactNode }) {
  const [reports, setReports] = useState<Report[]>(SEED_REPORTS);

  const updateReport = (id: string, patch: Partial<Report>) =>
    setReports((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));

  return createElement(
    ReportsContext.Provider,
    { value: { reports, updateReport } },
    children,
  );
}

export function useReports() {
  const ctx = useContext(ReportsContext);
  if (!ctx) throw new Error('useReports must be inside ReportsProvider');
  return ctx;
}