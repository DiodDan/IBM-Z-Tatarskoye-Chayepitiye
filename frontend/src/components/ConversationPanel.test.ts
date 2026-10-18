import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import ConversationPanel from './ConversationPanel.vue'
import type { Message } from '../types/api'
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() })
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
})
const mounted: ReturnType<typeof mount>[] = []
afterEach(() => { mounted.splice(0).forEach((wrapper) => wrapper.unmount()) })
const reply = (n: number): Message => ({ id: `reply-${n}`, role: 'assistant', content: `## Reply ${n}`, createdAt: '2026-10-18T10:00:00Z', suggestions: [`Follow up ${n}`] })

function panel(onSend = vi.fn(async () => true)) {
  const wrapper = mount(ConversationPanel, { props: { conversation: null, messages: [reply(1)], isLoadingHistory: false,
    isSending: false, error: null, sendError: null, onSend } })
  mounted.push(wrapper)
  return wrapper
}

describe('response follow-up actions', () => {
  it('sends suggestions from the first, second and third replies without reusing an earlier label', async () => {
    const onSend = vi.fn(async () => true)
    const wrapper = panel(onSend)
    for (let n = 1; n <= 3; n++) {
      await wrapper.setProps({ messages: Array.from({ length: n }, (_, i) => reply(i + 1)) })
      const button = wrapper.findAll('.suggestions button').at(-1)!
      await button.trigger('click'); await flushPromises()
      expect(onSend).toHaveBeenLastCalledWith(`Follow up ${n}`)
      expect(wrapper.findAll('h2').some((heading) => heading.text() === `Reply ${n}`)).toBe(true)
    }
    expect(onSend).toHaveBeenCalledTimes(3)
  })
  it('does not send from readonly admin or while waiting for a reply', async () => {
    const onSend = vi.fn(async () => true)
    const wrapper = panel(onSend)
    await wrapper.setProps({ readonly: true })
    await wrapper.get('.suggestions button').trigger('click')
    expect(onSend).not.toHaveBeenCalled()
    await wrapper.setProps({ readonly: false, isSending: true })
    await wrapper.get('.suggestions button').trigger('click')
    expect(onSend).not.toHaveBeenCalled()
  })
  it('restores actionable suggestions in older replies with malformed trailing brackets', async () => {
    const onSend = vi.fn(async () => true)
    const wrapper = panel(onSend)
    await wrapper.setProps({ messages: [reply(1), { ...reply(2), suggestions: [],
      content: '## Later reply\n\nAnswer.\n[[read more stories] | explore genres | get recommendations ]' }] })
    const buttons = wrapper.findAll('.suggestions button')
    expect(buttons.at(-1)!.text()).toBe('get recommendations')
    await buttons.at(-1)!.trigger('click')
    expect(onSend).toHaveBeenCalledWith('get recommendations')
    expect(wrapper.findAll('.markdown-body').at(-1)!.text()).not.toContain('[[read more')
  })

  it('renders and sends actions appended inline to the actual second response', async () => {
    const onSend = vi.fn(async () => true)
    const wrapper = panel(onSend)
    await wrapper.setProps({ messages: [reply(1), { ...reply(2), suggestions: [],
      content: 'A community garden is a shared space.\n\nFeel free to ask! [[more information] | additional resources | local examples | gardening tips]' }] })
    const buttons = wrapper.findAll('.suggestions button')
    expect(buttons.slice(1).map((button) => button.text())).toEqual(['more information', 'additional resources', 'local examples'])
    await buttons.at(-1)!.trigger('click')
    expect(onSend).toHaveBeenCalledWith('local examples')
    const content = wrapper.findAll('.markdown-body').at(-1)!.text()
    expect(content).toContain('Feel free to ask!')
    expect(content).not.toContain('[[more information')
  })

})
