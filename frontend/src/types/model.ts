export interface RiskScores {
  overall: number
  categories: Record<string, number>
  calibrationId: string
  calibrationApplied: boolean
}
export interface ModelAssessment {
  analysis: {
    status: 'completed' | 'pending' | 'failed' | 'disabled'
    modelFingerprint: string | null
    atomic: RiskScores | null
    trajectory: { id: string; responseCount: number; lastPosition: number; scores: RiskScores | null; associationScore: number | null } | null
    errorCode: string | null
    message: string | null
    coverage: { status: string; totalTokens: number; processedTokens: number; windowCount: number } | null
    windowPeak: { scores: RiskScores; peakTokenRanges: Record<string, { start: number; end: number }> } | null
  }
  membership: { ordinal: number; associationLogit: number | null; linkThreshold: number | null; status: string; updatedAt: string; expiresAt: string } | null
}
export const categoryLabels: Record<string, string> = {
  violence_weapons: 'Violence / weapons', bio_chemical: 'Biological / chemical',
  malicious_cyber: 'Malicious cyber', fraud: 'Fraud', illicit_evasion: 'Illicit evasion',
  hate_threats_harassment: 'Hate / threats / harassment', privacy_stalking: 'Privacy / stalking',
  sexual_exploitation: 'Sexual exploitation', self_harm_facilitation: 'Self-harm facilitation',
}
export const formatScore = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? '—' : value !== 0 && Math.abs(value) < 0.001 ? value.toExponential(2) : value.toFixed(3)

export interface TrajectoryDetail {
  id: string
  status: string
  modelFingerprint: string
  responseCount: number
  scores: RiskScores
  updatedAt: string
  members: { messageId: string; responseNumber: number | null; conversationId: string; content: string;
    ordinal: number; atomic: RiskScores; associationScore: number | null; associationLogit: number | null; linkThreshold: number | null }[]
}
