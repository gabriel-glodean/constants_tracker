import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ClassLookupForm } from './ClassLookupForm'
import * as classApi from '@/api/classApi'
import * as unitsApi from '@/api/unitsApi'

jest.mock('@/api/classApi')
jest.mock('@/api/unitsApi')
const mockedGet = classApi.getClassConstants as jest.MockedFunction<typeof classApi.getClassConstants>
const mockedGetUnits = unitsApi.getUnits as jest.MockedFunction<typeof unitsApi.getUnits>

async function fillAndSubmit(project = 'proj', version = '1') {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), project)
  await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), version)
  await user.click(screen.getByRole('button', { name: /load units/i }))
}

describe('ClassLookupForm', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders scope inputs and load button', () => {
    render(<ClassLookupForm />)
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /load units/i })).toBeInTheDocument()
  })

  it('shows validation error when project/version is empty', async () => {
    render(<ClassLookupForm />)
    expect(screen.getByRole('button', { name: /load units/i })).toBeDisabled()
    expect(mockedGetUnits).not.toHaveBeenCalled()
    expect(mockedGet).not.toHaveBeenCalled()
  })

  it('loads units and displays returned constants when a unit is clicked', async () => {
    mockedGetUnits.mockResolvedValue([
      { unitPath: 'app.jar', units: [{ name: 'com.example.MyClass', constants: 1 }] },
    ])
    mockedGet.mockResolvedValue({ constants: { 'SELECT *': ['METHOD_INVOCATION_PARAMETER'] } })

    render(<ClassLookupForm />)
    await fillAndSubmit()

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('com.example.MyClass')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /com\.example\.MyClass/i }))

    await waitFor(() => expect(screen.getByText('SELECT *')).toBeInTheDocument())
    expect(screen.getByText('METHOD_INVOCATION_PARAMETER')).toBeInTheDocument()
  })

  it('displays unit lookup error message on units API failure', async () => {
    mockedGetUnits.mockRejectedValue(new Error('Units lookup failed (HTTP 500)'))

    render(<ClassLookupForm />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('Units lookup failed (HTTP 500)')).toBeInTheDocument())
  })

  it('displays error message on class constants API failure', async () => {
    mockedGetUnits.mockResolvedValue([
      { unitPath: 'app.jar', units: [{ name: 'com.example.MyClass', constants: 1 }] },
    ])
    mockedGet.mockRejectedValue(new Error('Class/version not found.'))

    render(<ClassLookupForm />)
    await fillAndSubmit()

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('com.example.MyClass')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /com\.example\.MyClass/i }))

    await waitFor(() => expect(screen.getByText('Class/version not found.')).toBeInTheDocument())
  })

  it('shows fallback error for non-Error class lookup rejections', async () => {
    mockedGetUnits.mockResolvedValue([
      { unitPath: 'app.jar', units: [{ name: 'com.example.MyClass', constants: 1 }] },
    ])
    mockedGet.mockRejectedValue('oops')

    render(<ClassLookupForm />)
    await fillAndSubmit()

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('com.example.MyClass')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /com\.example\.MyClass/i }))

    await waitFor(() => expect(screen.getByText('Lookup failed')).toBeInTheDocument())
  })
})

