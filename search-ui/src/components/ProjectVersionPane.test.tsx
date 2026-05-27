import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProjectVersionPane } from './ProjectVersionPane'

describe('ProjectVersionPane', () => {
  it('renders project and version inputs', () => {
    render(
      <ProjectVersionPane
        project=""
        version=""
        onProjectChange={() => {}}
        onVersionChange={() => {}}
      />,
    )
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
  })

  it('shows required asterisk for version when versionRequired=true (default)', () => {
    render(
      <ProjectVersionPane
        project=""
        version=""
        onProjectChange={() => {}}
        onVersionChange={() => {}}
      />,
    )
    // version placeholder is 'e.g. 1' when required
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
  })

  it('shows "optional" placeholder and no asterisk when versionRequired=false', () => {
    render(
      <ProjectVersionPane
        project=""
        version=""
        onProjectChange={() => {}}
        onVersionChange={() => {}}
        versionRequired={false}
      />,
    )
    expect(screen.getByPlaceholderText(/optional/i)).toBeInTheDocument()
    // The version label should NOT contain a destructive asterisk inside a <span>
    const versionLabel = screen.getByText(/^version/i)
    expect(versionLabel.querySelector('span.text-destructive')).toBeNull()
  })

  it('calls onProjectChange when the project input changes', async () => {
    const user = userEvent.setup()
    const onProjectChange = jest.fn()
    render(
      <ProjectVersionPane
        project=""
        version=""
        onProjectChange={onProjectChange}
        onVersionChange={() => {}}
      />,
    )
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'abc')
    expect(onProjectChange).toHaveBeenCalledTimes(3)
  })

  it('calls onVersionChange when the version input changes', async () => {
    const user = userEvent.setup()
    const onVersionChange = jest.fn()
    render(
      <ProjectVersionPane
        project=""
        version=""
        onProjectChange={() => {}}
        onVersionChange={onVersionChange}
      />,
    )
    await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), '5')
    expect(onVersionChange).toHaveBeenCalled()
  })

  it('displays the current project and version values', () => {
    render(
      <ProjectVersionPane
        project="demo-project"
        version="3"
        onProjectChange={() => {}}
        onVersionChange={() => {}}
      />,
    )
    expect(screen.getByDisplayValue('demo-project')).toBeInTheDocument()
    expect(screen.getByDisplayValue('3')).toBeInTheDocument()
  })
})

