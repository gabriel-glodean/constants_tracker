import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UploadForm } from './UploadForm'
import * as uploadApi from '@/api/uploadApi'

jest.mock('@/api/uploadApi')
const mockedUploadClass = uploadApi.uploadClass as jest.MockedFunction<typeof uploadApi.uploadClass>
const mockedUploadJar = uploadApi.uploadJar as jest.MockedFunction<typeof uploadApi.uploadJar>
const mockedUploadConfig = uploadApi.uploadConfig as jest.MockedFunction<typeof uploadApi.uploadConfig>

function makeFile(name = 'Test.class', type = 'application/octet-stream') {
  return new File(['content'], name, { type })
}

describe('UploadForm', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders file type buttons, drop zone, and project input', () => {
    render(<UploadForm />)
    expect(screen.getByTitle(/single compiled .class/i)).toBeInTheDocument()
    expect(screen.getByTitle(/jar archive/i)).toBeInTheDocument()
    expect(screen.getByTitle(/config file/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled()
  })

  it('upload button stays disabled without a file', async () => {
    const user = userEvent.setup()
    render(<UploadForm />)
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'myproject')
    expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled()
  })

  it('shows file name after file is selected via input', async () => {
    const user = userEvent.setup()
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile('MyClass.class'))
    expect(screen.getByText('MyClass.class')).toBeInTheDocument()
  })

  it('shows file name after file is dropped', () => {
    render(<UploadForm />)
    const dropZone = screen.getByText(/drag & drop/i).closest('div')!
    fireEvent.drop(dropZone, {
      dataTransfer: { files: [makeFile('Dropped.class')] },
    })
    expect(screen.getByText('Dropped.class')).toBeInTheDocument()
  })

  it('calls uploadClass and shows success', async () => {
    const user = userEvent.setup()
    mockedUploadClass.mockResolvedValue({ status: 'success', message: 'Class uploaded successfully.' })
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile())
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(screen.getByText('Class uploaded successfully.')).toBeInTheDocument())
    expect(mockedUploadClass).toHaveBeenCalled()
  })

  it('calls uploadClass with version when version is provided', async () => {
    const user = userEvent.setup()
    mockedUploadClass.mockResolvedValue({ status: 'success', message: 'ok' })
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile())
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.type(screen.getByPlaceholderText(/leave blank/i), '3')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(mockedUploadClass).toHaveBeenCalledWith(expect.objectContaining({ version: 3 })))
  })

  it('switches to JAR type and calls uploadJar', async () => {
    const user = userEvent.setup()
    mockedUploadJar.mockResolvedValue({ status: 'success', message: 'JAR uploaded successfully.' })
    render(<UploadForm />)
    await user.click(screen.getByTitle(/jar archive/i))
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile('app.jar'))
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    // version field should be hidden for JAR
    expect(screen.queryByPlaceholderText(/leave blank/i)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(mockedUploadJar).toHaveBeenCalled())
    expect(screen.getByText('JAR uploaded successfully.')).toBeInTheDocument()
  })

  it('switches to config type and calls uploadConfig', async () => {
    const user = userEvent.setup()
    mockedUploadConfig.mockResolvedValue({ status: 'success', message: 'Config file uploaded successfully.' })
    render(<UploadForm />)
    await user.click(screen.getByTitle(/config file/i))
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile('app.yml', 'text/yaml'))
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(mockedUploadConfig).toHaveBeenCalled())
    expect(screen.getByText('Config file uploaded successfully.')).toBeInTheDocument()
  })

  it('shows error status returned from api', async () => {
    const user = userEvent.setup()
    mockedUploadClass.mockResolvedValue({ status: 'error', message: 'Invalid class file.' })
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile())
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(screen.getByText('Invalid class file.')).toBeInTheDocument())
  })

  it('shows error message when upload throws', async () => {
    const user = userEvent.setup()
    mockedUploadClass.mockRejectedValue(new Error('Network down'))
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile())
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(screen.getByText('Network down')).toBeInTheDocument())
  })

  it('shows fallback error for non-Error rejections', async () => {
    const user = userEvent.setup()
    mockedUploadClass.mockRejectedValue('bad')
    render(<UploadForm />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile())
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.click(screen.getByRole('button', { name: /upload/i }))
    await waitFor(() => expect(screen.getByText('Upload failed')).toBeInTheDocument())
  })
})

