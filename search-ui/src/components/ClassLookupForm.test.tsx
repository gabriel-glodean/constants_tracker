import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ClassLookupForm } from './ClassLookupForm'
import * as classApi from '@/api/classApi'
import * as metadataApi from '@/api/metadataApi'
import * as unitsApi from '@/api/unitsApi'

jest.mock('@/api/classApi')
jest.mock('@/api/metadataApi')
jest.mock('@/api/unitsApi')
const mockedGet = classApi.getClassConstants as jest.MockedFunction<typeof classApi.getClassConstants>
const mockedGetMetadata = metadataApi.getMetadata as jest.MockedFunction<typeof metadataApi.getMetadata>
const mockedGetUnits = unitsApi.getUnits as jest.MockedFunction<typeof unitsApi.getUnits>

async function fillAndSubmit(project = 'proj', version = '1') {
  const user = userEvent.setup()
  await waitFor(() => expect(screen.getByText(/metadata filters loaded/i)).toBeInTheDocument())
  await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), project)
  await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), version)
  await user.click(screen.getByRole('button', { name: /load units/i }))
}

describe('ClassLookupForm', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockedGetMetadata.mockResolvedValue({
      types: [
        { name: 'String', displayName: 'String' },
        { name: 'Long', displayName: 'Long' },
      ],
      usageTypes: [
        { name: 'METHOD_INVOCATION_PARAMETER', displayName: 'Method Invocation Parameter' },
        { name: 'FIELD_READ', displayName: 'Field Read' },
      ],
      semanticTypes: [
        { name: 'LOG_MESSAGE', displayName: 'Log Message' },
        { name: 'URL_RESOURCE', displayName: 'URL Resource' },
      ],
    })
  })

  it('renders scope inputs and load button', async () => {
    render(<ClassLookupForm />)
    await waitFor(() => expect(screen.getByText(/metadata filters loaded/i)).toBeInTheDocument())
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /load units/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /show advanced filters/i })).not.toBeInTheDocument()
  })

  it('shows validation error when project/version is empty', async () => {
    render(<ClassLookupForm />)
    await waitFor(() => expect(screen.getByText(/metadata filters loaded/i)).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /load units/i })).toBeDisabled()
    expect(mockedGetUnits).not.toHaveBeenCalled()
    expect(mockedGet).not.toHaveBeenCalled()
  })

  it('loads units and displays returned constants when a unit is clicked', async () => {
    mockedGetUnits.mockResolvedValue([
      { unitPath: 'app.jar', units: [{ name: 'com.example.MyClass', constants: 1 }] },
    ])
    mockedGet.mockResolvedValue({
      constants: [
        { value: 'SELECT *', valueType: 'String', usages: [{ structuralType: 'METHOD_INVOCATION_PARAMETER', semanticType: null }] },
      ],
    })

    render(<ClassLookupForm />)
    await fillAndSubmit()

    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('com.example.MyClass')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /com\.example\.MyClass/i }))

    await waitFor(() => expect(screen.getByText('SELECT *')).toBeInTheDocument())
    expect(screen.getByText('METHOD_INVOCATION_PARAMETER')).toBeInTheDocument()
  })

  it('applies selected metadata filters after units are already loaded', async () => {
    mockedGetUnits.mockResolvedValue([
      {
        unitPath: 'app.jar',
        units: [
          { name: 'com.example.MatchClass', constants: 2 },
          { name: 'com.example.OtherClass', constants: 1 },
        ],
      },
    ])
    mockedGet
      .mockResolvedValueOnce({
        constants: [
          { value: 'token', valueType: 'String', usages: [{ structuralType: 'METHOD_INVOCATION_PARAMETER', semanticType: null }] },
        ],
      })
      .mockResolvedValueOnce({
        constants: [
          { value: 'value', valueType: 'String', usages: [{ structuralType: 'FIELD_READ', semanticType: null }] },
        ],
      })

    render(<ClassLookupForm />)
    await waitFor(() => expect(mockedGetMetadata).toHaveBeenCalledTimes(1))
    await fillAndSubmit()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /show advanced filters/i }))
    await user.click(screen.getByRole('checkbox', { name: /method invocation parameter/i }))
    await user.click(screen.getByRole('button', { name: /apply filters/i }))

    await waitFor(() => {
      expect(screen.getByText('com.example.MatchClass')).toBeInTheDocument()
      expect(screen.queryByText('com.example.OtherClass')).not.toBeInTheDocument()
    })

    expect(mockedGetUnits).toHaveBeenCalledTimes(1)
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

