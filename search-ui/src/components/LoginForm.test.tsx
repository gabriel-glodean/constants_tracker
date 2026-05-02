import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginModal } from './LoginForm'

const mockSignIn = jest.fn()
const mockOnSuccess = jest.fn()
const mockOnClose = jest.fn()

function renderModal() {
  return render(
    <LoginModal signIn={mockSignIn} onSuccess={mockOnSuccess} onClose={mockOnClose} />
  )
}

beforeEach(() => {
  jest.clearAllMocks()
})

describe('LoginModal', () => {
  it('renders the password field and sign in button', () => {
    renderModal()
    expect(screen.getByPlaceholderText(/enter password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('sign in button is disabled when password is empty', () => {
    renderModal()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeDisabled()
  })

  it('sign in button becomes enabled when password is typed', async () => {
    const user = userEvent.setup()
    renderModal()
    await user.type(screen.getByPlaceholderText(/enter password/i), 'secret')
    expect(screen.getByRole('button', { name: /sign in/i })).toBeEnabled()
  })

  it('calls signIn and onSuccess on correct password', async () => {
    const user = userEvent.setup()
    mockSignIn.mockResolvedValue(undefined)
    renderModal()

    await user.type(screen.getByPlaceholderText(/enter password/i), 'admin')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(mockSignIn).toHaveBeenCalledWith('admin'))
    expect(mockOnSuccess).toHaveBeenCalledTimes(1)
    expect(screen.queryByText(/invalid/i)).not.toBeInTheDocument()
  })

  it('shows error message when signIn rejects', async () => {
    const user = userEvent.setup()
    mockSignIn.mockRejectedValue(new Error('Invalid password.'))
    renderModal()

    await user.type(screen.getByPlaceholderText(/enter password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(screen.getByText('Invalid password.')).toBeInTheDocument())
    expect(mockOnSuccess).not.toHaveBeenCalled()
  })

  it('shows fallback error for non-Error rejections', async () => {
    const user = userEvent.setup()
    mockSignIn.mockRejectedValue('boom')
    renderModal()

    await user.type(screen.getByPlaceholderText(/enter password/i), 'pw')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(screen.getByText('Login failed')).toBeInTheDocument())
  })

  it('calls onClose when the × button is clicked', async () => {
    const user = userEvent.setup()
    renderModal()
    await user.click(screen.getByLabelText(/close/i))
    expect(mockOnClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when the backdrop is clicked', () => {
    renderModal()
    // The backdrop is the outermost div (fixed inset-0)
    const backdrop = screen.getByPlaceholderText(/enter password/i).closest('div[class*="fixed"]')!
    fireEvent.click(backdrop)
    expect(mockOnClose).toHaveBeenCalledTimes(1)
  })

  it('does not call onClose when clicking inside the card', async () => {
    const user = userEvent.setup()
    renderModal()
    await user.click(screen.getByPlaceholderText(/enter password/i))
    expect(mockOnClose).not.toHaveBeenCalled()
  })

  it('clears the previous error when submitting again', async () => {
    const user = userEvent.setup()
    mockSignIn
      .mockRejectedValueOnce(new Error('Invalid password.'))
      .mockResolvedValueOnce(undefined)

    renderModal()
    const input = screen.getByPlaceholderText(/enter password/i)
    const btn = screen.getByRole('button', { name: /sign in/i })

    await user.type(input, 'bad')
    await user.click(btn)
    await waitFor(() => expect(screen.getByText('Invalid password.')).toBeInTheDocument())

    await user.click(btn)
    await waitFor(() => expect(screen.queryByText('Invalid password.')).not.toBeInTheDocument())
  })
})

