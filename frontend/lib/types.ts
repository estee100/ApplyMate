// Mirrors the backend DTOs. Keep in sync with backend/src/main/java/com/applymate/dto.

export type ApplicationStatus =
  | "APPLIED"
  | "ONLINE_ASSESSMENT"
  | "INTERVIEWING"
  | "OFFER"
  | "REJECTED"
  | "WITHDRAWN";

export type JobSource = "ADZUNA" | "HIMALAYAS" | "PASTED";
export type JobType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERNSHIP";
export type DocumentKind = "RESUME" | "COVER_LETTER";
export type CommunicationKind = "EMAIL" | "CALL" | "MEETING" | "MESSAGE" | "OTHER";
export type CommunicationDirection = "RECEIVED" | "SENT";

export interface AuthResponse {
  token: string;
  refreshToken: string;
  expiresIn: number;
  email: string;
  name: string | null;
}

export interface MatchSummary {
  percentage: number;
  metCount: number;
  missingCount: number;
}

export interface JobCard {
  id: number;
  title: string;
  company: string;
  companyLogoUrl: string | null;
  location: string | null;
  salary: string | null;
  url: string | null;
  publisher: string | null;
  source: JobSource;
  jobType: JobType | null;
  postedDate: string | null;
  saved: boolean;
  applicationId: number | null;
  applicationStatus: ApplicationStatus | null;
  match: MatchSummary | null;
}

export interface MatchResponse {
  matchPercentage: number;
  requirementsMet: string[];
  requirementsMissing: string[];
  rationale: string | null;
  modelUsed: string | null;
  generatedAt: string;
}

export interface DocumentSummary {
  id: number;
  kind: DocumentKind;
  modelUsed: string | null;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Application {
  id: number;
  jobId: number | null;
  jobTitle: string;
  companyName: string;
  companyLogoUrl: string | null;
  location: string | null;
  jobPostingUrl: string | null;
  status: ApplicationStatus;
  dateApplied: string;
  statusUpdatedAt: string;
  notes: string | null;
  nextAction: string | null;
  nextActionDate: string | null;
  needsFollowUp: boolean;
  manual: boolean;
  createdAt: string;
  /** Optimistic-lock version; send it back on updates. */
  version: number;
}

export interface JobDetail {
  job: JobCard;
  description: string | null;
  analyzable: boolean;
  match: MatchResponse | null;
  application: Application | null;
  documents: DocumentSummary[];
  hasResume: boolean;
}

export interface FeedResponse {
  jobs: JobCard[];
  sources: string[];
  notice: string | null;
  hasMore: boolean;
  page: number;
}

export interface Recommendation {
  job: JobCard;
  matchedSkills: string[];
  relevance: number;
}

export interface Recommendations {
  items: Recommendation[];
  searchedTitles: string[];
  location: string | null;
  includeRemote: boolean;
  notice: string | null;
}

export interface ApplicationEvent {
  id: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  note: string | null;
  changedAt: string;
}

export interface Communication {
  id: number;
  applicationId: number;
  kind: CommunicationKind;
  direction: CommunicationDirection;
  occurredAt: string;
  contactName: string | null;
  subject: string | null;
  summary: string | null;
}

export interface ApplicationStats {
  total: number;
  byStatus: Record<ApplicationStatus, number>;
  active: number;
  offers: number;
  needsFollowUp: number;
}

export interface AnalyticsSummary {
  totalApplications: number;
  byStatus: Record<ApplicationStatus, number>;
  applicationsByMonth: { month: string; count: number }[];
  responseRatePct: number | null;
  interviewRatePct: number | null;
  offerRatePct: number | null;
  avgDaysToResponse: number | null;
}

export interface ResumeInfo {
  id: number;
  fileName: string;
  contentType: string;
  characters: number;
  active: boolean;
  hasMaster: boolean;
  uploadedAt: string;
}

export interface SkillProfile {
  skills: string[];
  jobTitles: string[];
  domains: string[];
  locations: string[];
  seniority: string | null;
  yearsExperience: number | null;
  summary: string | null;
  modelUsed: string | null;
  generatedAt: string;
}

export interface Profile {
  email: string;
  name: string | null;
  preferredLocation: string | null;
  includeRemote: boolean;
  emailReminders: boolean;
  resume: ResumeInfo | null;
  skillProfile: SkillProfile | null;
  ai: { enabled: boolean; usedToday: number; dailyLimit: number };
  memberSince: string;
}

export interface UploadResponse {
  resume: ResumeInfo;
  skillProfile: SkillProfile | null;
  warning: string | null;
}

export interface ResumeEntry {
  title: string;
  meta: string;
  right: string;
  subtitle: string;
  subtitleRight: string;
  bullets: string[];
}

export interface ResumeSection {
  heading: string;
  entries: ResumeEntry[];
  bullets: string[];
}

export interface ResumeContent {
  fullName: string;
  headline: string;
  contactLine: string;
  summary: string;
  sections: ResumeSection[];
  skills: string[];
}

export interface CoverLetterContent {
  greeting: string;
  paragraphs: string[];
  closing: string;
}

export interface MasterResume {
  content: ResumeContent;
  saved: boolean;
  version: number;
}

export interface GeneratedDocument {
  id: number;
  kind: DocumentKind;
  jobId: number;
  jobTitle: string;
  company: string;
  content: ResumeContent | CoverLetterContent;
  original: ResumeContent | CoverLetterContent;
  edited: boolean;
  modelUsed: string | null;
  authorName: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface HistoryEntry {
  jobId: number | null;
  jobTitle: string;
  company: string;
  companyLogoUrl: string | null;
  url: string | null;
  publisher: string | null;
  application: Application | null;
  documents: DocumentSummary[];
  lastActivity: string;
}

export interface PrepFocus {
  topic: string;
  why: string;
  actions: string[];
}

export interface InterviewPrep {
  content: {
    summary: string;
    focusAreas: PrepFocus[];
    likelyQuestions: { question: string; howToAnswer: string }[];
    questionsToAsk: string[];
    watchOuts: string[];
  };
  modelUsed: string | null;
  generatedAt: string;
}

export interface CalendarEvent {
  id: string;
  type: "APPLIED" | "NEXT_ACTION" | "STATUS_CHANGE";
  date: string;
  title: string;
  detail: string | null;
  applicationId: number;
  overdue: boolean;
}
