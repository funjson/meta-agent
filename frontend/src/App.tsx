import { FormEvent, KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties, PointerEvent as ReactPointerEvent, ReactNode } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  Cursor as IslandCursor,
  Icon as IslandIcon,
  Loading as IslandLoading,
  Title as IslandTitle,
} from 'animal-island-ui'
import 'animal-island-ui/style'
import 'animal-island-ui/es/components/Cursor/cursor.css'
import islandItem022 from 'animal-island-ui/items/item-022.png'
import islandItem074 from 'animal-island-ui/items/item-074.png'
import islandItem129 from 'animal-island-ui/items/item-129.png'

type MessageView = {
  id: string
  role: 'USER' | 'ASSISTANT'
  messageType: string
  content: string
  jobId: string | null
  taskRunId: string | null
  createdAt: string
}

type ConversationView = {
  id: string
  agentProfileId: string
  title: string
  status: string
  defaultProviderId: string
  activeJobId: string | null
  version: number
  messages: MessageView[]
}

type ConversationFileView = {
  id: string
  conversationId: string
  fileName: string
  contentType: string
  sizeBytes: number
  checksumSha256: string
  status: string
  createdAt: string
}

type ControlDecisionView = {
  id: string
  controlTurnId: string
  intentType: string
  goalSummary: string
  decisionSummary: string
  constraints: string[]
}

type JobView = {
  id: string
  goalSummary: string
  providerId: string
  status: string
}

type TaskRunView = {
  id: string
  status: string
  resultSummary: string | null
}

type ChatTurnResult = {
  controlTurnId: string
  conversation: ConversationView
  controlDecision: ControlDecisionView
  job: JobView | null
  taskRun: TaskRunView | null
}

type AgentPathNode = {
  id: string
  parentId: string | null
  nodeType: string
  label: string
  status: string
  summary: string | null
  occurredAt: string
}

type AgentPathView = {
  conversationId: string
  nodes: AgentPathNode[]
}

type PathDisplayMode = 'compact' | 'debug'

type ProviderConfigView = {
  id: string
  providerType: string
  displayName: string
  baseUrl: string
  modelName: string
  enabled: boolean
  configured: boolean
  secretSource: string
  version: number
}

type ModelCapabilities = {
  toolCalling: boolean
  reasoning: boolean
  reasoningContent: boolean
  thinkingMode: boolean
  vision: boolean
}

type ModelSpecView = {
  id: string
  displayName: string
  providerId: string
  providerModel: string
  family: string
  contextWindow: number
  modalities: string[]
  capabilities: ModelCapabilities
}

type ModelCatalogView = {
  defaultModelId: string
  fallbackModelId: string
  models: ModelSpecView[]
}

type ProviderDraft = {
  baseUrl: string
  apiKey: string
}

type ProviderTestResult = {
  success: boolean
  model: string
  latencyMs: number
  message: string
}

type SystemInfoView = {
  chatTransport?: {
    responseMode?: string
    streamingEnabled?: boolean
  }
}

type ThemeName = 'animal-official' | 'animal-meta'

type StreamingAssistant = {
  id: string | null
  messageType: string
  content: string
}

type CuteSelectOption = {
  value: string
  label: string
  meta?: string
  icon?: ReactNode
  disabled?: boolean
}

type SseFrame = {
  event: string
  data: string
}

const conversationStorageKey = 'meta-agent.active-conversation'
const themeStorageKey = 'meta-agent.theme'

const themeOptions: CuteSelectOption[] = [
  { value: 'animal-official', label: 'Animal Official', meta: '官方岛屿资产', icon: '🏝️' },
  { value: 'animal-meta', label: 'Meta Animal', meta: '我们自己的岛屿皮肤', icon: '🍃' },
]

export function App() {
  const [conversation, setConversation] = useState<ConversationView | null>(null)
  const [conversations, setConversations] = useState<ConversationView[]>([])
  const [path, setPath] = useState<AgentPathNode[]>([])
  const [files, setFiles] = useState<ConversationFileView[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [awaitingAssistant, setAwaitingAssistant] = useState(false)
  const [pendingAssistantAfter, setPendingAssistantAfter] = useState<number | null>(null)
  const [streamingAssistant, setStreamingAssistant] = useState<StreamingAssistant | null>(null)
  const [uploadingFiles, setUploadingFiles] = useState(false)
  const [showThinking, setShowThinking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copyNotice, setCopyNotice] = useState<string | null>(null)
  const [systemInfo, setSystemInfo] = useState<SystemInfoView | null>(null)
  const [theme, setTheme] = useState<ThemeName>(() => {
    const stored = localStorage.getItem(themeStorageKey)
    if (stored === 'animal-island') return 'animal-meta'
    return themeOptions.some((option) => option.value === stored) ? stored as ThemeName : 'animal-official'
  })
  const [booting, setBooting] = useState(true)
  const [railHistoryRatio, setRailHistoryRatio] = useState(68)
  const [inspectorWidth, setInspectorWidth] = useState(390)
  const [providerMode, setProviderMode] = useState('auto')
  const [providerPanelOpen, setProviderPanelOpen] = useState(false)
  const [modelCatalog, setModelCatalog] = useState<ModelCatalogView | null>(null)
  const [providerConfigs, setProviderConfigs] = useState<ProviderConfigView[]>([])
  const [providerDrafts, setProviderDrafts] = useState<Record<string, ProviderDraft>>({})
  const [settingsModelId, setSettingsModelId] = useState('deepseek-chat')
  const [providerBusy, setProviderBusy] = useState(false)
  const [providerTest, setProviderTest] = useState<ProviderTestResult | null>(null)
  const [pathMode, setPathMode] = useState<PathDisplayMode>('compact')
  const [collapsedPathNodes, setCollapsedPathNodes] = useState<Set<string>>(
    () => new Set(),
  )
  const messagesRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const streamingChatEnabled = systemInfo?.chatTransport?.streamingEnabled ?? true
  const assistantIsPending = awaitingAssistant || Boolean(streamingAssistant)
  const composerBusy = sending || assistantIsPending
  const shellStyle = {
    '--rail-history-ratio': `${railHistoryRatio}%`,
    '--inspector-width': `${inspectorWidth}px`,
    '--island-item-a': `url(${islandItem022})`,
    '--island-item-b': `url(${islandItem074})`,
    '--island-item-c': `url(${islandItem129})`,
  } as CSSProperties & Record<string, string>

  const selectedSettingsModel = useMemo(
    () => modelCatalog?.models.find((model) => model.id === settingsModelId)
      ?? modelCatalog?.models.find((model) => model.id === providerMode)
      ?? modelCatalog?.models.find((model) => model.providerId !== 'fake')
      ?? modelCatalog?.models[0]
      ?? null,
    [modelCatalog, providerMode, settingsModelId],
  )

  const selectedProviderConfig = useMemo(
    () => providerConfigs.find((provider) => provider.id === selectedSettingsModel?.providerId) ?? null,
    [providerConfigs, selectedSettingsModel?.providerId],
  )

  const selectedProviderDraft = selectedSettingsModel
    ? providerDrafts[selectedSettingsModel.providerId]
    : undefined

  const currentExecutorModel = useMemo(
    () => modelCatalog?.models.find((model) => model.id === providerMode) ?? null,
    [modelCatalog, providerMode],
  )

  const executorConfigured = currentExecutorModel?.providerId === 'fake'
    || providerConfigs.find((provider) => provider.id === currentExecutorModel?.providerId)?.configured

  const executorOptions = useMemo<CuteSelectOption[]>(() => [
    {
      value: 'auto',
      label: 'Auto',
      meta: '由框架选择可用模型',
      icon: '🛩️',
    },
    ...(modelCatalog?.models.map((model) => {
      const configured = model.providerId === 'fake'
        || providerConfigs.find((provider) => provider.id === model.providerId)?.configured
      return {
        value: model.id,
        label: model.displayName,
        meta: `${model.providerId} · ${formatContextWindow(model.contextWindow)}${configured ? '' : ' · 待配置'}`,
        icon: modelIcon(model),
        disabled: !configured && model.providerId !== 'fake',
      }
    }) ?? []),
  ], [modelCatalog, providerConfigs])

  const settingsModelOptions = useMemo<CuteSelectOption[]>(
    () => modelCatalog?.models.map((model) => ({
      value: model.id,
      label: model.displayName,
      meta: `${model.providerId} · ${model.providerModel}`,
      icon: modelIcon(model),
    })) ?? [],
    [modelCatalog],
  )

  const loadPath = useCallback(async (conversationId: string) => {
    const response = await fetch(`/api/v1/conversations/${conversationId}/agent-path`)
    if (!response.ok) {
      throw new Error(`执行路径加载失败（${response.status}）`)
    }
    const value = (await response.json()) as AgentPathView
    setPath(value.nodes)
  }, [])

  const loadConversations = useCallback(async () => {
    const response = await fetch('/api/v1/conversations')
    if (!response.ok) {
      throw new Error(`会话列表加载失败（${response.status}）`)
    }
    const value = (await response.json()) as ConversationView[]
    setConversations(value)
    return value
  }, [])

  const loadFiles = useCallback(async (conversationId: string) => {
    const response = await fetch(`/api/v1/conversations/${conversationId}/files`)
    if (!response.ok) {
      throw new Error(`文件列表加载失败（${response.status}）`)
    }
    const value = (await response.json()) as ConversationFileView[]
    setFiles(value)
    return value
  }, [])

  const loadConversation = useCallback(async (conversationId: string) => {
    const response = await fetch(`/api/v1/conversations/${conversationId}`)
    if (!response.ok) {
      throw new Error(`对话加载失败（${response.status}）`)
    }
    const value = (await response.json()) as ConversationView
    localStorage.setItem(conversationStorageKey, value.id)
    setConversation(value)
    await Promise.all([
      loadPath(value.id),
      loadFiles(value.id),
    ])
    return value
  }, [loadFiles, loadPath])

  const createConversation = useCallback(async () => {
    const response = await fetch('/api/v1/conversations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agentProfileId: 'general-agent',
        providerId: 'auto',
      }),
    })
    if (!response.ok) {
      throw new Error(`创建对话失败（${response.status}）`)
    }
    const value = (await response.json()) as ConversationView
    localStorage.setItem(conversationStorageKey, value.id)
    setConversation(value)
    setConversations((current) => [
      value,
      ...current.filter((item) => item.id !== value.id),
    ])
    setPath([])
    setFiles([])
    setCollapsedPathNodes(new Set())
    return value
  }, [])

  const loadProviders = useCallback(async () => {
    const response = await fetch('/api/v1/settings/providers')
    if (!response.ok) {
      throw new Error(`Provider 配置加载失败（${response.status}）`)
    }
    const providers = (await response.json()) as ProviderConfigView[]
    setProviderConfigs(providers)
    setProviderDrafts((current) => {
      const next = { ...current }
      providers.forEach((provider) => {
        next[provider.id] = {
          baseUrl: next[provider.id]?.baseUrl ?? provider.baseUrl,
          apiKey: next[provider.id]?.apiKey ?? '',
        }
      })
      return next
    })
  }, [])

  const loadModels = useCallback(async () => {
    const response = await fetch('/api/v1/settings/models')
    if (!response.ok) {
      throw new Error(`模型目录加载失败（${response.status}）`)
    }
    const catalog = (await response.json()) as ModelCatalogView
    setModelCatalog(catalog)
    setSettingsModelId((value) => value || catalog.defaultModelId)
    return catalog
  }, [])

  const loadSystemInfo = useCallback(async () => {
    const response = await fetch('/api/v1/system/info')
    if (!response.ok) {
      throw new Error(`System info failed (${response.status})`)
    }
    const value = (await response.json()) as SystemInfoView
    setSystemInfo(value)
    return value
  }, [])

  useEffect(() => {
    async function initialize() {
      setError(null)
      try {
        const [, catalog] = await Promise.all([loadProviders(), loadModels(), loadSystemInfo()])
        setProviderMode((value) => value || catalog.defaultModelId)
        const knownConversations = await loadConversations()
        const storedId = localStorage.getItem(conversationStorageKey)
        const startupConversationId = storedId
          && knownConversations.some((item) => item.id === storedId)
          ? storedId
          : knownConversations[0]?.id
        if (startupConversationId) {
          try {
            await loadConversation(startupConversationId)
            return
          } catch {
            localStorage.removeItem(conversationStorageKey)
          }
        }
        await createConversation()
      } catch (requestError: unknown) {
        setError(messageOf(requestError))
      } finally {
        setBooting(false)
      }
    }
    initialize()
  }, [createConversation, loadConversation, loadConversations, loadModels, loadProviders, loadSystemInfo])

  useEffect(() => {
    localStorage.setItem(themeStorageKey, theme)
  }, [theme])

  useEffect(() => {
    if (!conversation?.id) return undefined
    const intervalMs = awaitingAssistant ? 800 : 2500
    const timer = window.setInterval(() => {
      void loadConversation(conversation.id)
        .then((value) => {
          if (pendingAssistantAfter !== null
              && hasAssistantAfter(value, pendingAssistantAfter)) {
            setAwaitingAssistant(false)
            setPendingAssistantAfter(null)
            setStreamingAssistant(null)
          }
        })
        .catch(() => undefined)
    }, intervalMs)
    return () => window.clearInterval(timer)
  }, [awaitingAssistant, conversation?.id, loadConversation, pendingAssistantAfter])

  useEffect(() => {
    if (!assistantIsPending) {
      setShowThinking(false)
      return undefined
    }
    const timer = window.setTimeout(() => {
      setShowThinking(true)
    }, 250)
    return () => window.clearTimeout(timer)
  }, [assistantIsPending])

  useEffect(() => {
    const messages = messagesRef.current
    if (!messages) return
    // 只滚动消息容器，避免 scrollIntoView 推动整个页面并把底部输入框移出视口。
    messages.scrollTo({
      top: messages.scrollHeight,
      behavior: 'smooth',
    })
  }, [conversation?.messages.length, showThinking, streamingAssistant?.content])

  const displayPath = useMemo(() => (
    pathMode === 'debug'
      ? path
      : path.filter(isCompactPathNode)
  ), [path, pathMode])

  const pathDepths = useMemo(() => {
    const byId = new Map(displayPath.map((node) => [node.id, node]))
    const depths = new Map<string, number>()
    function depth(node: AgentPathNode, visited = new Set<string>()): number {
      const known = depths.get(node.id)
      if (known !== undefined) return known
      if (!node.parentId || visited.has(node.id)) return 0
      visited.add(node.id)
      const parent = byId.get(node.parentId)
      const value = parent ? Math.min(depth(parent, visited) + 1, 12) : 0
      depths.set(node.id, value)
      return value
    }
    displayPath.forEach((node) => depth(node))
    return depths
  }, [displayPath])

  const pathChildren = useMemo(() => {
    const children = new Map<string, AgentPathNode[]>()
    displayPath.forEach((node) => {
      if (!node.parentId) return
      const siblings = children.get(node.parentId) ?? []
      siblings.push(node)
      children.set(node.parentId, siblings)
    })
    return children
  }, [displayPath])

  const visiblePath = useMemo(() => {
    const byId = new Map(displayPath.map((node) => [node.id, node]))
    // 接口返回扁平路径；只要任一祖先收起，当前节点就不参与渲染。
    return displayPath.filter((node) => {
      let parentId = node.parentId
      const visited = new Set<string>()
      while (parentId && !visited.has(parentId)) {
        if (collapsedPathNodes.has(parentId)) return false
        visited.add(parentId)
        parentId = byId.get(parentId)?.parentId ?? null
      }
      return true
    })
  }, [collapsedPathNodes, displayPath])

  /** 在同一路径节点上重复点击时切换其子树的展开状态。 */
  function togglePathNode(nodeId: string) {
    if (!pathChildren.has(nodeId)) return
    setCollapsedPathNodes((current) => {
      const next = new Set(current)
      if (next.has(nodeId)) {
        next.delete(nodeId)
      } else {
        next.add(nodeId)
      }
      return next
    })
  }

  /** 收起所有拥有子节点的路径节点，根节点仍保持可见。 */
  function collapseAllPathNodes() {
    setCollapsedPathNodes(
      new Set(
        path
          .filter((node) => pathChildren.has(node.id))
          .map((node) => node.id),
      ),
    )
  }

  function beginRailResize(event: ReactPointerEvent<HTMLDivElement>) {
    const container = event.currentTarget.parentElement
    if (!container) return
    const rect = container.getBoundingClientRect()
    const onMove = (moveEvent: PointerEvent) => {
      const next = ((moveEvent.clientY - rect.top) / rect.height) * 100
      setRailHistoryRatio(clamp(next, 42, 82))
    }
    const stop = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', stop)
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', stop)
  }

  function beginInspectorResize(event: ReactPointerEvent<HTMLDivElement>) {
    const startX = event.clientX
    const startWidth = inspectorWidth
    const onMove = (moveEvent: PointerEvent) => {
      setInspectorWidth(clamp(startWidth + startX - moveEvent.clientX, 300, 680))
    }
    const stop = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', stop)
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', stop)
  }

  async function sendMessage(event?: FormEvent) {
    event?.preventDefault()
    const content = draft.trim()
    if (!content || !conversation || sending) return
    const activeConversation = conversation
    const pendingAfter = activeConversation.messages.length

    const optimistic: MessageView = {
      id: `optimistic-${crypto.randomUUID()}`,
      role: 'USER',
      messageType: 'TEXT',
      content,
      jobId: null,
      taskRunId: null,
      createdAt: new Date().toISOString(),
    }
    setConversation({
      ...conversation,
      messages: [...conversation.messages, optimistic],
    })
    setDraft('')
    setSending(true)
    setAwaitingAssistant(true)
    setPendingAssistantAfter(pendingAfter)
    setStreamingAssistant(null)
    setError(null)

    try {
      if (streamingChatEnabled) {
        await sendMessageStream(activeConversation.id, content, pendingAfter)
        return
      }
      const response = await fetch(`/api/v1/conversations/${activeConversation.id}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          content,
          providerId: providerMode,
        }),
      })
      if (!response.ok) {
        throw new Error(await apiError(response, '消息发送失败'))
      }
      const result = (await response.json()) as ChatTurnResult
      setConversation(result.conversation)
      if (hasAssistantAfter(result.conversation, pendingAfter)) {
        setAwaitingAssistant(false)
        setPendingAssistantAfter(null)
      }
      await Promise.all([
        loadPath(result.conversation.id),
        loadConversations(),
      ])
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
      await loadConversation(activeConversation.id).catch(() => undefined)
      setAwaitingAssistant(false)
      setPendingAssistantAfter(null)
      setStreamingAssistant(null)
    } finally {
      setSending(false)
    }
  }

  async function sendMessageStream(
    conversationId: string,
    content: string,
    pendingAfter: number,
  ) {
    const response = await fetch(`/api/v1/conversations/${conversationId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': crypto.randomUUID(),
      },
      body: JSON.stringify({
        content,
        providerId: providerMode,
      }),
    })
    if (!response.ok || !response.body) {
      throw new Error(await apiError(response, 'stream message failed'))
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (value) {
        buffer += decoder.decode(value, { stream: !done })
        buffer = buffer.replace(/\r\n/g, '\n')
        buffer = await consumeSseBuffer(buffer, conversationId, pendingAfter)
      }
      if (done) break
    }
    if (buffer.trim()) {
      await handleSseFrame(parseSseFrame(buffer), conversationId, pendingAfter)
    }
  }

  async function consumeSseBuffer(
    buffer: string,
    conversationId: string,
    pendingAfter: number,
  ) {
    let rest = buffer
    while (true) {
      const splitIndex = rest.indexOf('\n\n')
      if (splitIndex < 0) return rest
      const rawFrame = rest.slice(0, splitIndex)
      rest = rest.slice(splitIndex + 2)
      await handleSseFrame(parseSseFrame(rawFrame), conversationId, pendingAfter)
    }
  }

  async function handleSseFrame(
    frame: SseFrame,
    conversationId: string,
    pendingAfter: number,
  ) {
    if (!frame.data) return
    if (frame.event === 'turn') {
      const result = JSON.parse(frame.data) as ChatTurnResult
      const assistantAlreadyStored = hasAssistantAfter(result.conversation, pendingAfter)
      setConversation(
        assistantAlreadyStored
          ? hideAssistantMessagesAfter(result.conversation, pendingAfter)
          : result.conversation,
      )
      if (assistantAlreadyStored) {
        setAwaitingAssistant(false)
        setPendingAssistantAfter(null)
        setStreamingAssistant(null)
      }
      await Promise.all([
        loadPath(result.conversation.id),
        loadConversations(),
      ])
      return
    }
    if (frame.event === 'conversation') {
      setConversation(JSON.parse(frame.data) as ConversationView)
      return
    }
    if (frame.event === 'assistant_start') {
      const start = JSON.parse(frame.data) as { id: string; messageType: string }
      setStreamingAssistant({
        id: start.id,
        messageType: start.messageType,
        content: '',
      })
      return
    }
    if (frame.event === 'assistant_delta') {
      const delta = JSON.parse(frame.data) as { content: string }
      setStreamingAssistant((current) => ({
        id: current?.id ?? null,
        messageType: current?.messageType ?? 'TASK_RESULT',
        content: `${current?.content ?? ''}${delta.content ?? ''}`,
      }))
      return
    }
    if (frame.event === 'assistant_done') {
      setAwaitingAssistant(false)
      setPendingAssistantAfter(null)
      setStreamingAssistant(null)
      await Promise.all([
        loadConversation(conversationId),
        loadConversations(),
      ])
      return
    }
    if (frame.event === 'done') {
      const done = JSON.parse(frame.data) as { assistantMessageEmitted: boolean }
      if (!done.assistantMessageEmitted) {
        setAwaitingAssistant(false)
        setPendingAssistantAfter(null)
        setStreamingAssistant(null)
      }
      await loadPath(conversationId).catch(() => undefined)
      return
    }
    if (frame.event === 'error') {
      const event = JSON.parse(frame.data) as { message?: string }
      throw new Error(event.message || 'stream failed')
    }
  }

  async function uploadFiles(selected: FileList | null) {
    if (!selected?.length || !conversation || uploadingFiles) return
    setUploadingFiles(true)
    setError(null)
    try {
      for (const file of Array.from(selected)) {
        const body = new FormData()
        body.append('file', file)
        const response = await fetch(`/api/v1/conversations/${conversation.id}/files`, {
          method: 'POST',
          body,
        })
        if (!response.ok) {
          throw new Error(await apiError(response, `文件 ${file.name} 上传失败`))
        }
      }
      await loadFiles(conversation.id)
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
    } finally {
      setUploadingFiles(false)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void sendMessage()
    }
  }

  async function startNewConversation() {
    setError(null)
    try {
      setDraft('')
      await createConversation()
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
    }
  }

  async function selectConversation(conversationId: string) {
    if (conversation?.id === conversationId) return
    setError(null)
    setDraft('')
    setCollapsedPathNodes(new Set())
    try {
      await loadConversation(conversationId)
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
    }
  }

  async function copyToClipboard(label: string, text: string) {
    if (!text.trim()) return
    await navigator.clipboard.writeText(text)
    setCopyNotice(`${label}已复制`)
    window.setTimeout(() => setCopyNotice(null), 1600)
  }

  function exportChat() {
    if (!conversation) return ''
    const lines = [
      `# Conversation Export`,
      `conversationId: ${conversation.id}`,
      `title: ${conversation.title}`,
      `activeJobId: ${conversation.activeJobId ?? 'null'}`,
      '',
      `files: ${files.length}`,
      ...files.map((file) => `- ${file.fileName} (${file.id}, ${file.contentType}, ${file.sizeBytes} bytes)`),
      '',
      ...conversation.messages.map((message, index) => [
        `## Message ${index + 1}`,
        `id: ${message.id}`,
        `role: ${message.role}`,
        `type: ${message.messageType}`,
        `jobId: ${message.jobId ?? 'null'}`,
        `taskRunId: ${message.taskRunId ?? 'null'}`,
        `createdAt: ${message.createdAt}`,
        '',
        message.content,
      ].join('\n')),
    ]
    return lines.join('\n\n')
  }

  function exportPath() {
    if (!conversation) return ''
    const byId = new Map(path.map((node) => [node.id, node]))
    function depthOf(node: AgentPathNode) {
      let depth = 0
      let parentId = node.parentId
      const visited = new Set<string>()
      while (parentId && !visited.has(parentId)) {
        visited.add(parentId)
        depth += 1
        parentId = byId.get(parentId)?.parentId ?? null
      }
      return depth
    }
    return [
      `# Agent Path Export`,
      `conversationId: ${conversation.id}`,
      '',
      ...path.map((node) => {
        const indent = '  '.repeat(depthOf(node))
        return [
          `${indent}- ${node.nodeType} · ${node.label}`,
          `${indent}  id: ${node.id}`,
          `${indent}  parentId: ${node.parentId ?? 'null'}`,
          `${indent}  status: ${node.status}`,
          `${indent}  summary: ${node.summary ?? ''}`,
          `${indent}  occurredAt: ${node.occurredAt}`,
        ].join('\n')
      }),
    ].join('\n')
  }

  async function saveSelectedProviderConfig() {
    if (!selectedSettingsModel || !selectedProviderConfig || !selectedProviderDraft) return
    setProviderBusy(true)
    setProviderTest(null)
    setError(null)
    try {
      const response = await fetch(`/api/v1/settings/providers/${selectedSettingsModel.providerId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          baseUrl: selectedProviderDraft.baseUrl,
          modelName: selectedSettingsModel.providerModel,
          apiKey: selectedProviderDraft.apiKey || null,
          persistSecret: false,
          enabled: true,
          expectedVersion: selectedProviderConfig.version,
        }),
      })
      if (!response.ok) {
        throw new Error(await apiError(response, 'Provider 配置保存失败'))
      }
      const updated = (await response.json()) as ProviderConfigView
      setProviderConfigs((current) => current.map((provider) => (
        provider.id === updated.id ? updated : provider
      )))
      setProviderDrafts((current) => ({
        ...current,
        [updated.id]: {
          baseUrl: updated.baseUrl,
          apiKey: '',
        },
      }))
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
    } finally {
      setProviderBusy(false)
    }
  }

  async function testSelectedProvider() {
    if (!selectedSettingsModel || !selectedProviderDraft) return
    setProviderBusy(true)
    setProviderTest(null)
    setError(null)
    try {
      const response = await fetch(`/api/v1/settings/providers/${selectedSettingsModel.providerId}/test`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          apiKey: selectedProviderDraft.apiKey || null,
          modelId: selectedSettingsModel.id,
        }),
      })
      if (!response.ok) {
        throw new Error(await apiError(response, `${selectedSettingsModel.displayName} 连接测试失败`))
      }
      setProviderTest((await response.json()) as ProviderTestResult)
      setProviderDrafts((current) => ({
        ...current,
        [selectedSettingsModel.providerId]: {
          baseUrl: current[selectedSettingsModel.providerId]?.baseUrl ?? selectedProviderDraft.baseUrl,
          apiKey: '',
        },
      }))
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
    } finally {
      setProviderBusy(false)
    }
  }

  return (
    <IslandCursor forceAll className="island-cursor-root">
    <main className="app-shell" data-theme={theme} style={shellStyle}>
      {booting ? (
        <div className="island-boot-screen">
          {theme === 'animal-official' ? (
            <IslandLoading active />
          ) : (
            <div className="meta-island-loader">
              <span />
              <span />
              <span />
            </div>
          )}
          <div className="island-boot-title">
            {theme === 'animal-official' ? (
              <IslandTitle size="middle" color="app-green">Meta Agent Island</IslandTitle>
            ) : (
              <strong>Meta Agent Island</strong>
            )}
            <p>Preparing your agent workbench...</p>
          </div>
        </div>
      ) : null}
      <aside className="rail">
        <div className="brand-mark">
          <span className="brand-orbit" />
          <div>
            {theme === 'animal-official' ? (
              <IslandTitle size="small" color="app-green">Meta Agent</IslandTitle>
            ) : (
              <strong>Meta Agent</strong>
            )}
            <small>CONTROL + LOOP</small>
          </div>
        </div>

        <button className="new-chat" type="button" onClick={startNewConversation}>
          <span>🌱</span>
          新对话
        </button>

        <div className="rail-split">
        <div className="rail-section conversations-section">
          <span className="rail-label">CONVERSATIONS</span>
          <div className="conversation-list">
            {conversations.length ? conversations.map((item) => (
              <button
                className={`conversation-item ${item.id === conversation?.id ? 'active' : ''}`}
                type="button"
                key={item.id}
                onClick={() => void selectConversation(item.id)}
              >
                <span className="conversation-icon" aria-hidden="true">🍃</span>
                <strong>{item.title}</strong>
                <small>
                  {item.activeJobId ? `JOB ${item.activeJobId.slice(0, 8)}` : 'NO ACTIVE JOB'}
                </small>
              </button>
            )) : (
              <div className="conversation-empty">暂无历史会话</div>
            )}
          </div>
        </div>

        <div
          className="rail-resizer"
          role="separator"
          aria-orientation="horizontal"
          title="拖动调整会话历史和 AgentProfile 的高度"
          onPointerDown={beginRailResize}
        />

        <div className="rail-section profile-section">
          <span className="rail-label">AGENT PROFILE</span>
          <div className="profile-card">
            <span className="profile-icon">A</span>
            <div>
              <strong>General Agent</strong>
              <small>{conversation?.agentProfileId ?? '正在连接'}</small>
            </div>
            <span className="online-dot" />
          </div>
        </div>

        </div>

        <button
          className="rail-action"
          type="button"
          onClick={() => setProviderPanelOpen((value) => !value)}
        >
          <span>⚙️</span>
          模型设置
          <small>{executorConfigured ? '已配置' : '待配置'}</small>
        </button>
      </aside>

      <section className="chat-stage">
        <header className="chat-header">
          <div>
            <strong>{conversation?.title ?? '正在初始化对话'}</strong>
            <span>
              {conversation?.activeJobId
                ? `ACTIVE JOB · ${conversation.activeJobId.slice(0, 8)}`
                : '对话会先经过 Control 意图识别'}
            </span>
          </div>
          <div className="header-selects">
            <CuteSelect
              label="EXECUTOR"
              value={providerMode}
              options={executorOptions}
              onChange={setProviderMode}
            />
            <CuteSelect
              label="THEME"
              value={theme}
              options={themeOptions}
              onChange={(value) => setTheme(value as ThemeName)}
            />
          </div>
          <button
            className="copy-button"
            type="button"
            disabled={!conversation?.messages.length}
            onClick={() => void copyToClipboard('聊天记录', exportChat())}
          >
            复制聊天
          </button>
        </header>

        <div className="messages" ref={messagesRef}>
          {conversation?.messages.length ? (
            conversation.messages.map((message) => (
              <article className={`message ${message.role.toLowerCase()}`} key={message.id}>
                <div className="avatar">
                  <IslandIcon name={message.role === 'USER' ? 'icon-miles' : 'icon-design'} size={22} />
                </div>
                <div className="message-body">
                  <div className="message-meta">
                    <strong>{message.role === 'USER' ? 'You' : 'Meta Agent'}</strong>
                    <span>{message.messageType.replaceAll('_', ' ')}</span>
                  </div>
                  <div className="message-content">
                    <MarkdownMessage content={message.content} />
                  </div>
                  {message.jobId ? (
                    <div className="message-link">JOB · {message.jobId.slice(0, 8)}</div>
                  ) : null}
                </div>
              </article>
            ))
          ) : (
            <div className="empty-chat">
              <span className="empty-symbol">⌁</span>
              <h1>把目标直接说出来。</h1>
              <p>
                Control 会识别意图、创建 Job 与 Task；Loop Kernel 负责执行。
                右侧会同步出现可审计的执行路径。
              </p>
              <div className="suggestions">
                <button type="button" onClick={() => setDraft('分析当前项目并给出下一步可执行的开发计划')}>
                  分析项目并规划下一步
                </button>
                <button type="button" onClick={() => setDraft('设计一个支持中断恢复和评测的长任务执行方案')}>
                  设计长任务恢复方案
                </button>
              </div>
            </div>
          )}

          {streamingAssistant ? (
            <article className="message assistant streaming">
              <div className="avatar">
                <IslandIcon name="icon-design" size={22} />
              </div>
              <div className="message-body">
                <div className="message-meta">
                  <strong>Meta Agent</strong>
                  <span>{streamingAssistant.messageType.replaceAll('_', ' ')}</span>
                </div>
                <div className="message-content">
                  {streamingAssistant.content ? (
                    <MarkdownMessage content={streamingAssistant.content} />
                  ) : null}
                </div>
                <div className="thinking-line compact">
                  <i />
                  <i />
                  <i />
                  streaming...
                </div>
              </div>
            </article>
          ) : showThinking ? (
            <article className="message assistant">
              <div className="avatar">
                <IslandIcon name="icon-design" size={22} />
              </div>
              <div className="message-body">
                <div className="message-meta">
                  <strong>Meta Agent</strong>
                  <span>处理中</span>
                </div>
                <div className="thinking-line">
                  <i />
                  <i />
                  <i />
                  正在处理你的消息…
                </div>
              </div>
            </article>
          ) : null}
        </div>

        <div className="composer-wrap">
          {error ? <div className="error-banner">{error}</div> : null}
          {copyNotice ? <div className="copy-toast">{copyNotice}</div> : null}
          {files.length ? (
            <div className="attachment-strip">
              <span>当前文件</span>
              <div>
                {files.map((file) => (
                  <button
                    className="attachment-chip"
                    type="button"
                    key={file.id}
                    title={`${file.fileName} · ${file.contentType} · ${file.sizeBytes} bytes`}
                    onClick={() => setDraft((value) => (
                      value.trim()
                        ? value
                        : `请根据我上传的文件「${file.fileName}」回答。`
                    ))}
                  >
                    <span>📄</span>
                    {file.fileName}
                    <small>{formatBytes(file.sizeBytes)}</small>
                  </button>
                ))}
              </div>
            </div>
          ) : null}
          <form className="chat-composer" onSubmit={sendMessage}>
            <input
              ref={fileInputRef}
              className="file-input"
              type="file"
              multiple
              onChange={(event) => void uploadFiles(event.target.files)}
            />
            <button
              className="attach-button"
              type="button"
              disabled={!conversation || uploadingFiles}
              onClick={() => fileInputRef.current?.click()}
              title="上传文件"
            >
              {uploadingFiles ? '…' : '＋'}
            </button>
            <textarea
              aria-label="给 Meta Agent 发消息"
              placeholder="描述你想完成的事情…"
              value={draft}
              rows={1}
              disabled={!conversation || composerBusy}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleComposerKeyDown}
            />
            <button type="submit" disabled={!draft.trim() || !conversation || composerBusy}>
              {composerBusy ? '...' : '↑'}
            </button>
          </form>
          <p>＋ 上传文件 · Enter 发送 · Shift + Enter 换行 · 执行路径不包含模型隐藏思维</p>
        </div>
      </section>

      <div
        className="inspector-resizer"
        role="separator"
        aria-orientation="vertical"
        title="Drag to resize Agent Path"
        onPointerDown={beginInspectorResize}
      />

      <aside className="inspector">
        <header className="inspector-header">
          <div>
            <span className="eyebrow">AGENT PATH</span>
            <strong>思考与执行路径</strong>
          </div>
          <span className="path-count">
            {pathMode === 'compact' ? `${displayPath.length}/${path.length}` : path.length}
          </span>
        </header>

        <div className="path-legend">
          <span><i className="control-color" />Control</span>
          <span><i className="loop-color" />Loop</span>
          <span><i className="clarification-color" />Clarification</span>
          <span><i className="evidence-color" />Evidence</span>
          <span><i className="web-evidence-color" />Web Research</span>
          {path.length ? (
            <div className="path-actions">
              <button
                className={pathMode === 'compact' ? 'active' : ''}
                type="button"
                onClick={() => setPathMode('compact')}
              >
                简洁
              </button>
              <button
                className={pathMode === 'debug' ? 'active' : ''}
                type="button"
                onClick={() => setPathMode('debug')}
              >
                调试
              </button>
              <button type="button" onClick={() => setCollapsedPathNodes(new Set())}>
                全部展开
              </button>
              <button type="button" onClick={collapseAllPathNodes}>
                全部收起
              </button>
              <button type="button" onClick={() => void copyToClipboard('执行路径', exportPath())}>
                复制路径
              </button>
            </div>
          ) : null}
        </div>

        <div className="path-list">
          {displayPath.length ? (
            visiblePath.map((node) => {
              const hasChildren = pathChildren.has(node.id)
              const collapsed = collapsedPathNodes.has(node.id)
              return (
              <article
                className={`path-node type-${node.nodeType.toLowerCase()}`}
                key={node.id}
                style={{ '--depth': pathDepths.get(node.id) ?? 0 } as React.CSSProperties}
              >
                <span className="path-line" />
                <span className="path-dot" />
                <button
                  className="path-node-card"
                  type="button"
                  disabled={!hasChildren}
                  aria-expanded={hasChildren ? !collapsed : undefined}
                  onClick={() => togglePathNode(node.id)}
                >
                  <div className="path-node-head">
                    <strong>
                      {hasChildren ? (
                        <span className={`path-chevron ${collapsed ? 'collapsed' : ''}`}>⌄</span>
                      ) : null}
                      {node.label}
                    </strong>
                    <span>{node.status}</span>
                  </div>
                  {node.summary ? <p>{node.summary}</p> : null}
                  <small>{formatTime(node.occurredAt)}</small>
                </button>
              </article>
              )
            })
          ) : (
            <div className="empty-path">
              <div className="path-placeholder">
                <span />
                <span />
                <span />
              </div>
              <strong>还没有执行轨迹</strong>
              <p>发送一条任务消息后，这里会从 ControlDecision 开始生长。</p>
            </div>
          )}
        </div>
      </aside>

      {providerPanelOpen ? (
        <div className="settings-backdrop" onMouseDown={() => setProviderPanelOpen(false)}>
          <section className="settings-panel" onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div>
                <span className="eyebrow">EXECUTOR MODEL</span>
                <h2>模型设置</h2>
              </div>
              <button type="button" onClick={() => setProviderPanelOpen(false)}>×</button>
            </header>

            <CuteSelect
              className="settings-model-select"
              label="选择执行模型"
              value={settingsModelId}
              options={settingsModelOptions}
              onChange={(value) => {
                setSettingsModelId(value)
                setProviderMode(value)
                setProviderTest(null)
              }}
            />

            {selectedSettingsModel ? (
              <div className="model-capability-card">
                <strong>{selectedSettingsModel.displayName}</strong>
                <span>{selectedSettingsModel.providerModel}</span>
                <p>
                  上下文 {formatContextWindow(selectedSettingsModel.contextWindow)}
                  {' · '}
                  {selectedSettingsModel.modalities.join(' / ')}
                </p>
                <div>
                  <i className={selectedSettingsModel.capabilities.toolCalling ? 'on' : ''}>Tool</i>
                  <i className={selectedSettingsModel.capabilities.reasoning ? 'on' : ''}>Reasoning</i>
                  <i className={selectedSettingsModel.capabilities.thinkingMode ? 'on' : ''}>Thinking</i>
                  <i className={selectedSettingsModel.capabilities.vision ? 'on' : ''}>Vision</i>
                </div>
              </div>
            ) : null}

            {selectedSettingsModel?.providerId === 'fake' ? (
              <div className="secret-note">
                Fake 模型不需要 API Key，适合离线链路验证。
              </div>
            ) : (
              <>
                <label>
                  <span>Provider</span>
                  <input value={selectedProviderConfig?.displayName ?? selectedSettingsModel?.providerId ?? ''} disabled />
                </label>
                <label>
                  <span>BASE URL</span>
                  <input
                    value={selectedProviderDraft?.baseUrl ?? ''}
                    onChange={(event) => {
                      if (!selectedSettingsModel) return
                      const providerId = selectedSettingsModel.providerId
                      setProviderDrafts((current) => ({
                        ...current,
                        [providerId]: {
                          baseUrl: event.target.value,
                          apiKey: current[providerId]?.apiKey ?? '',
                        },
                      }))
                    }}
                  />
                </label>
                <label>
                  <span>API KEY</span>
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder={selectedProviderConfig?.configured ? '•••••••• 已配置，留空保持不变' : 'API Key'}
                    value={selectedProviderDraft?.apiKey ?? ''}
                    onChange={(event) => {
                      if (!selectedSettingsModel) return
                      const providerId = selectedSettingsModel.providerId
                      setProviderDrafts((current) => ({
                        ...current,
                        [providerId]: {
                          baseUrl: current[providerId]?.baseUrl ?? selectedProviderConfig?.baseUrl ?? '',
                          apiKey: event.target.value,
                        },
                      }))
                    }}
                  />
                </label>
              </>
            )}

            <div className="secret-note">
              先选择 executor 模型，再配置该模型所属 Provider 的 API Key。
              Key 不会回传浏览器，也不会写入代码、事件、日志或 Checkpoint；页面提交的 Key 当前只保存在后端进程内存。
            </div>

            {providerTest ? (
              <div className={`test-result ${providerTest.success ? 'success' : ''}`}>
                {providerTest.success ? '连接成功' : '连接失败'} · {providerTest.model} · {providerTest.latencyMs}ms
              </div>
            ) : null}

            <div className="settings-actions">
              <button
                type="button"
                disabled={providerBusy || !selectedSettingsModel || selectedSettingsModel.providerId === 'fake'}
                onClick={testSelectedProvider}
              >
                测试连接
              </button>
              <button
                className="primary"
                type="button"
                disabled={providerBusy || !selectedSettingsModel || selectedSettingsModel.providerId === 'fake'}
                onClick={saveSelectedProviderConfig}
              >
                保存到内存
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
    </IslandCursor>
  )
}

function modelIcon(model: ModelSpecView) {
  if (model.capabilities.vision) return '👀'
  if (model.capabilities.reasoning || model.capabilities.thinkingMode) return '🧠'
  if (model.providerId === 'fake') return '🧪'
  if (model.providerId === 'glm') return '🦉'
  if (model.providerId === 'deepseek') return '🐋'
  return '🌿'
}

function CuteSelect({
  className,
  label,
  value,
  options,
  onChange,
}: {
  className?: string
  label: string
  value: string
  options: CuteSelectOption[]
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const selected = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    if (!open) return
    const closeWhenOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', closeWhenOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeWhenOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  return (
    <div className={`cute-select ${className ?? ''}`} ref={rootRef}>
      <span className="cute-select-label">{label}</span>
      <button
        className={`cute-select-trigger ${open ? 'open' : ''}`}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="cute-select-icon" aria-hidden="true">{selected?.icon ?? '🌿'}</span>
        <span className="cute-select-value">
          <strong>{selected?.label ?? '请选择'}</strong>
          {selected?.meta ? <small>{selected.meta}</small> : null}
        </span>
        <span className="cute-select-arrow" aria-hidden="true">⌄</span>
      </button>
      {open ? (
        <div className="cute-select-menu" role="listbox" aria-label={label}>
          {options.map((option) => (
            <button
              className={`cute-select-option ${option.value === value ? 'selected' : ''}`}
              type="button"
              role="option"
              aria-selected={option.value === value}
              disabled={option.disabled}
              key={option.value}
              onClick={() => {
                if (option.disabled) return
                onChange(option.value)
                setOpen(false)
              }}
            >
              <span className="cute-select-icon" aria-hidden="true">{option.icon ?? '🌿'}</span>
              <span className="cute-select-value">
                <strong>{option.label}</strong>
                {option.meta ? <small>{option.meta}</small> : null}
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

async function apiError(response: Response, fallback: string) {
  try {
    const body = await response.json() as { message?: string }
    return body.message || `${fallback}（${response.status}）`
  } catch {
    return `${fallback}（${response.status}）`
  }
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : '发生了未知错误'
}

function formatTime(value: string) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function formatContextWindow(value: number) {
  if (!value) return '未知'
  if (value >= 1000) return `${Math.round(value / 1000)}K`
  return String(value)
}

function isCompactPathNode(node: AgentPathNode) {
  return ![
    'LOOP_PHASE',
    'MODEL_CALL',
    'CHECKPOINT',
    'RECOVERY_ATTEMPT',
  ].includes(node.nodeType)
}

function hasAssistantAfter(conversation: ConversationView, index: number) {
  return conversation.messages.slice(index).some((message) => message.role === 'ASSISTANT')
}

function hideAssistantMessagesAfter(conversation: ConversationView, index: number): ConversationView {
  return {
    ...conversation,
    messages: conversation.messages.filter((message, messageIndex) => (
      messageIndex < index || message.role !== 'ASSISTANT'
    )),
  }
}

function parseSseFrame(raw: string): SseFrame {
  const lines = raw.split(/\r?\n/)
  let event = 'message'
  const data: string[] = []
  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
      return
    }
    if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).trimStart())
    }
  })
  return { event, data: data.join('\n') }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
    </div>
  )
}
