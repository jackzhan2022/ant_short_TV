import { describe, expect, it } from 'vitest';
import access from '@/access';
import { workspaceLandingPath } from './navigation';

const member = { currentUser: { userid: '1' }, selectedTenant: { membership: { status: 'ACTIVE' } } };
describe('menu-aware workspace entry', () => {
  it('opens the permitted menu instead of a forbidden default page', () => {
    expect(workspaceLandingPath(access({ ...member, tenantPermissions: ['MENU:FREE_CREATION'] }))).toBe('/free-creation');
    expect(workspaceLandingPath(access({ ...member, tenantPermissions: ['MENU:PROJECTS'] }))).toBe('/projects/list');
    expect(workspaceLandingPath(access({ ...member, tenantPermissions: ['MENU:TEAM_MANAGEMENT'] }))).toBe('/team/my');
    expect(workspaceLandingPath(access({ ...member, tenantPermissions: [] }))).toBe('/exception/403');
  });
  it('keeps onboarding available when no team is selected', () => {
    expect(workspaceLandingPath(access({ currentUser: member.currentUser }))).toBe('/team/my');
  });
});
