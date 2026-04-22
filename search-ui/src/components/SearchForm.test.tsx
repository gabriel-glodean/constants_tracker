import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SearchForm } from './SearchForm'
import type { SearchParams } from '@/api/searchApi'

function renderForm(onSearch = jest.fn(), isLoading = false) {
  render(<SearchForm onSearch={onSearch} isLoading={isLoading} />)
  return { onSearch }
}

describe('SearchForm', () => {
  it('renders search input and submit button', () => {
    renderForm()
    expect(screen.getByPlaceholderText(/search constants/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^search$/i })).toBeInTheDocument()
  })

  it('submit button disabled when term is empty', () => {
    renderForm()
    expect(screen.getByRole('button', { name: /^search$/i })).toBeDisabled()
  })

  it('submit button disabled when isLoading is true', async () => {
    const user = userEvent.setup()
    renderForm(jest.fn(), true)
    await user.type(screen.getByPlaceholderText(/search constants/i), 'hello')
    expect(screen.getByRole('button', { name: /^search$/i })).toBeDisabled()
  })

  it('calls onSearch with default params when submitted', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderForm()
    await user.type(screen.getByPlaceholderText(/search constants/i), 'SELECT')
    await user.click(screen.getByRole('button', { name: /^search$/i }))
    expect(onSearch).toHaveBeenCalledWith<[SearchParams]>({
      project: '*',
      term: 'SELECT',
      fuzzy: 1,
      rows: 25,
    })
  })

  it('does not call onSearch when term is blank', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderForm()
    await user.type(screen.getByPlaceholderText(/search constants/i), '   ')
    fireEvent.submit(document.querySelector('form')!)
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('toggles advanced options panel', async () => {
    const user = userEvent.setup()
    renderForm()
    expect(screen.queryByPlaceholderText(/\* \(all projects\)/i)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /show advanced/i }))
    expect(screen.getByPlaceholderText(/\* \(all projects\)/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /hide advanced/i }))
    expect(screen.queryByPlaceholderText(/\* \(all projects\)/i)).not.toBeInTheDocument()
  })

  it('changes fuzzy tolerance and sends correct value', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderForm()
    await user.click(screen.getByRole('button', { name: /show advanced/i }))
    await user.click(screen.getByRole('button', { name: /exact/i }))
    await user.type(screen.getByPlaceholderText(/search constants/i), 'foo')
    await user.click(screen.getByRole('button', { name: /^search$/i }))
    expect(onSearch).toHaveBeenCalledWith(expect.objectContaining({ fuzzy: 0 }))
  })

  it('changes max results and sends correct value', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderForm()
    await user.click(screen.getByRole('button', { name: /show advanced/i }))
    await user.selectOptions(screen.getByRole('combobox'), '50')
    await user.type(screen.getByPlaceholderText(/search constants/i), 'bar')
    await user.click(screen.getByRole('button', { name: /^search$/i }))
    expect(onSearch).toHaveBeenCalledWith(expect.objectContaining({ rows: 50 }))
  })

  it('shows project filter label when project is not *', async () => {
    const user = userEvent.setup()
    renderForm()
    await user.click(screen.getByRole('button', { name: /show advanced/i }))
    const projectInput = screen.getByPlaceholderText(/\* \(all projects\)/i)
    await user.clear(projectInput)
    await user.type(projectInput, 'my-project')
    expect(screen.getByText('my-project')).toBeInTheDocument()
  })
})

