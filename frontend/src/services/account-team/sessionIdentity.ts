// Deliberately tab-local: shared storage would let an old tab silently adopt a new identity.
let expectedSessionId: string | undefined;
let sessionChanged = false;
export const SESSION_CONTEXT_EVENT = 'auth-session-context-changed';

export const getExpectedSessionId = () => expectedSessionId;
export const getSessionChanged = () => sessionChanged;
const notifySessionContextChange = () => {
  // Bootstrap can be applied inside React's state updater; notify subscribers after it returns.
  queueMicrotask(() => window.dispatchEvent(new Event(SESSION_CONTEXT_EVENT)));
};

export function setExpectedSessionId(sessionId: string | undefined) {
  expectedSessionId = sessionId;
  sessionChanged = false;
  notifySessionContextChange();
}

export function markSessionChanged() {
  sessionChanged = true;
  notifySessionContextChange();
}

export type ImpersonationDisplay = { tenantName: string; userName: string };
const DISPLAY_KEY = 'impersonationDisplay';

// Display-only recovery hint; authorization and return identity always come from the server cookie.
export function getImpersonationDisplay(): ImpersonationDisplay | undefined {
  try {
    const display = JSON.parse(sessionStorage.getItem(DISPLAY_KEY) || 'null');
    return typeof display?.tenantName === 'string' && typeof display?.userName === 'string'
      ? display : undefined;
  } catch {
    return undefined;
  }
}

export function setImpersonationDisplay(display?: ImpersonationDisplay) {
  if (display) sessionStorage.setItem(DISPLAY_KEY, JSON.stringify(display));
  else sessionStorage.removeItem(DISPLAY_KEY);
  notifySessionContextChange();
}
