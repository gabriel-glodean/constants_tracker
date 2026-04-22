import { uploadClass, uploadJar, uploadConfig } from './uploadApi'
const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())
const file = new File(['x'], 'Test.class', { type: 'application/octet-stream' })
describe('uploadClass', () => {
  it('POSTs without version and returns success', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    const result = await uploadClass({ file, project: 'proj' })
    expect(mockFetch).toHaveBeenCalledWith('/class?project=proj', expect.objectContaining({ method: 'POST' }))
    expect(result).toEqual({ status: 'success', message: 'Class uploaded successfully.' })
  })
  it('PUTs with version', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    await uploadClass({ file, project: 'proj', version: 3 })
    expect(mockFetch).toHaveBeenCalledWith('/class?project=proj&version=3', expect.objectContaining({ method: 'PUT' }))
  })
  it('returns error on 422', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 422 })
    const result = await uploadClass({ file, project: 'proj' })
    expect(result).toEqual({ status: 'error', message: 'Invalid class file.' })
  })
  it('returns generic error on other status', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    const result = await uploadClass({ file, project: 'proj' })
    expect(result.message).toContain('500')
  })
})
describe('uploadJar', () => {
  it('POSTs and returns success', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    const result = await uploadJar({ file, project: 'proj', jarName: 'app.jar' })
    expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/jar?'), expect.objectContaining({ method: 'POST' }))
    expect(result).toEqual({ status: 'success', message: 'JAR uploaded successfully.' })
  })
  it('returns error on 422', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 422 })
    const result = await uploadJar({ file, project: 'proj', jarName: 'app.jar' })
    expect(result).toEqual({ status: 'error', message: 'Invalid JAR file.' })
  })
  it('returns generic error on other status', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503 })
    const result = await uploadJar({ file, project: 'proj', jarName: 'app.jar' })
    expect(result.message).toContain('503')
  })
})
describe('uploadConfig', () => {
  it('POSTs without version and returns success', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    const result = await uploadConfig({ file, project: 'proj' })
    expect(mockFetch).toHaveBeenCalledWith('/config?project=proj', expect.anything())
    expect(result).toEqual({ status: 'success', message: 'Config file uploaded successfully.' })
  })
  it('PUTs with version', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    await uploadConfig({ file, project: 'proj', version: 2 })
    expect(mockFetch).toHaveBeenCalledWith('/config?project=proj&version=2', expect.anything())
  })
  it('returns error on 422', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 422 })
    const result = await uploadConfig({ file, project: 'proj' })
    expect(result).toEqual({ status: 'error', message: 'Unsupported config file type.' })
  })
  it('returns generic error on other status', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    const result = await uploadConfig({ file, project: 'proj' })
    expect(result.message).toContain('500')
  })
})
