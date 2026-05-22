import { getJarJobs, JarJob } from './jarJobsApi';

const mockFetch = jest.fn();
global.fetch = mockFetch;

const mockOk = (body: unknown) => ({ ok: true, status: 200, json: () => Promise.resolve(body) });
const mockErr = (status: number) => ({ ok: false, status, json: () => Promise.resolve({}) });

const JOB: JarJob = {
  project: 'my-app',
  version: 1,
  jarName: 'app.jar',
  status: 'COMPLETED',
  startedAt: '2026-05-21T10:00:00Z',
  lastUpdatedAt: '2026-05-21T10:01:00Z',
  nestedTotal: 5,
  nestedProcessed: 5,
  nestedFailed: 0,
  errorMessage: null,
};

beforeEach(() => mockFetch.mockReset());

describe('getJarJobs', () => {
  it('fetches all jobs for a project/version', async () => {
    mockFetch.mockResolvedValue(mockOk([JOB]));
    const result = await getJarJobs('my-app', 1);
    expect(mockFetch).toHaveBeenCalledWith('/jar/jobs?project=my-app&version=1', { method: 'GET' });
    expect(result).toEqual([JOB]);
  });

  it('includes jarName param when provided', async () => {
    mockFetch.mockResolvedValue(mockOk([JOB]));
    await getJarJobs('my-app', 1, 'app.jar');
    expect(mockFetch).toHaveBeenCalledWith(
      '/jar/jobs?project=my-app&version=1&jarName=app.jar',
      { method: 'GET' },
    );
  });

  it('returns empty array on 404', async () => {
    mockFetch.mockResolvedValue(mockErr(404));
    const result = await getJarJobs('my-app', 1);
    expect(result).toEqual([]);
  });

  it('throws on other HTTP errors', async () => {
    mockFetch.mockResolvedValue(mockErr(500));
    await expect(getJarJobs('my-app', 1)).rejects.toThrow('Jar jobs fetch failed (HTTP 500)');
  });

  it('ignores blank jarName', async () => {
    mockFetch.mockResolvedValue(mockOk([]));
    await getJarJobs('my-app', 1, '   ');
    expect(mockFetch).toHaveBeenCalledWith('/jar/jobs?project=my-app&version=1', { method: 'GET' });
  });
});
