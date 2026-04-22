import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ClassLookupForm } from './ClassLookupForm'
import * as classApi from '@/api/classApi'

jest.mock('@/api/classApi')
const mockedGet = classApi.getClassConstants as jest.MockedFunction<typeof classApi.getClassConstants>

async function fillAndSubmit(project = 'proj', className = 'com/Foo', version = '1') {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), project)
  await user.type(screen.getByPlaceholderText('com/example/MyClass'), className)
  await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), version)
  await user.click(screen.getByRole('button', { name: /lookup/i }))
}

describe('ClassLookupForm', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders all inputs and the submit button', () => {
    render(<ClassLookupForm />)
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('com/example/MyClass')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /lookup/i })).toBeInTheDocument()
  })

  it('shows validation error when any field is empty', async () => {
    const user = userEvent.setup()
    render(<ClassLookupForm />)
    // submit with empty fields
    await user.click(screen.getByRole('button', { name: /lookup/i }))
    expect(screen.getByText('All fields are required.')).toBeInTheDocument()
    expect(mockedGet).not.toHaveBeenCalled()
  })

  it('displays returned constants on success', async () => {
    mockedGet.mockResolvedValue({ constants: { 'SELECT *': ['METHOD_INVOCATION_PARAMETER'] } })
    render(<ClassLookupForm />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('SELECT *')).toBeInTheDocument())
    expect(screen.getByText('METHOD_INVOCATION_PARAMETER')).toBeInTheDocument()
  })

  it('displays error message on API failure', async () => {
    mockedGet.mockRejectedValue(new Error('Class/version not found.'))
    render(<ClassLookupForm />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('Class/version not found.')).toBeInTheDocument())
  })

  it('shows fallback error for non-Error rejections', async () => {
    mockedGet.mockRejectedValue('oops')
    render(<ClassLookupForm />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('Lookup failed')).toBeInTheDocument())
  })
})

