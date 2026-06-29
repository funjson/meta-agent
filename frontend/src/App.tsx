import { FormEvent, KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'

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

const conversationStorageKey = 'meta-agent.active-conversation'

export function App() {
  const [conversation, setConversation] = useState<ConversationView | null>(null)
  const [conversations, setConversations] = useState<ConversationView[]>([])
  const [path, setPath] = useState<AgentPathNode[]>([])
  const [files, setFiles] = useState<ConversationFileView[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [uploadingFiles, setUploadingFiles] = useState(false)
  const [showThinking, setShowThinking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copyNotice, setCopyNotice] = useState<string | null>(null)
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

  useEffect(() => {
    async function initialize() {
      setError(null)
      try {
        const [, catalog] = await Promise.all([loadProviders(), loadModels()])
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
      }
    }
    initialize()
  }, [createConversation, loadConversation, loadConversations, loadModels, loadProviders])

  useEffect(() => {
    if (!conversation?.id || sending) return undefined
    const timer = window.setInterval(() => {
      void loadConversation(conversation.id).catch(() => undefined)
    }, 2500)
    return () => window.clearInterval(timer)
  }, [conversation?.id, loadConversation, sending])

  useEffect(() => {
    if (!sending) {
      setShowThinking(false)
      return undefined
    }
    const timer = window.setTimeout(() => {
      setShowThinking(true)
    }, 250)
    return () => window.clearTimeout(timer)
  }, [sending])

  useEffect(() => {
    const messages = messagesRef.current
    if (!messages) return
    // 只滚动消息容器，避免 scrollIntoView 推动整个页面并把底部输入框移出视口。
    messages.scrollTo({
      top: messages.scrollHeight,
      behavior: 'smooth',
    })
  }, [conversation?.messages.length, showThinking])

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

  async function sendMessage(event?: FormEvent) {
    event?.preventDefault()
    const content = draft.trim()
    if (!content || !conversation || sending) return
    const activeConversation = conversation

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
    setError(null)

    try {
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
      await Promise.all([
        loadPath(result.conversation.id),
        loadConversations(),
      ])
    } catch (requestError: unknown) {
      setError(messageOf(requestError))
      await loadConversation(activeConversation.id).catch(() => undefined)
    } finally {
      setSending(false)
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
    <main className="app-shell">
      <aside className="rail">
        <div className="brand-mark">
          <span className="brand-orbit" />
          <div>
            <strong>Meta Agent</strong>
            <small>CONTROL + LOOP</small>
          </div>
        </div>

        <button className="new-chat" type="button" onClick={startNewConversation}>
          <span>＋</span>
          新对话
        </button>

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

        <div className="rail-section">
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

        <div className="rail-spacer" />

        <button
          className="rail-action"
          type="button"
          onClick={() => setProviderPanelOpen((value) => !value)}
        >
          <span>⚙</span>
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
          <label className="provider-mode">
            <span>EXECUTOR</span>
            <select value={providerMode} onChange={(event) => setProviderMode(event.target.value)}>
              <option value="auto">Auto</option>
              {modelCatalog?.models.map((model) => {
                const configured = model.providerId === 'fake'
                  || providerConfigs.find((provider) => provider.id === model.providerId)?.configured
                return (
                  <option key={model.id} value={model.id} disabled={!configured && model.providerId !== 'fake'}>
                    {model.displayName}{configured ? '' : '（待配置）'}
                  </option>
                )
              })}
            </select>
          </label>
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
                <div className="avatar">{message.role === 'USER' ? '你' : 'A'}</div>
                <div className="message-body">
                  <div className="message-meta">
                    <strong>{message.role === 'USER' ? 'You' : 'Meta Agent'}</strong>
                    <span>{message.messageType.replaceAll('_', ' ')}</span>
                  </div>
                  <div className="message-content">{message.content}</div>
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

          {showThinking ? (
            <article className="message assistant">
              <div className="avatar">A</div>
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
              disabled={!conversation || sending}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleComposerKeyDown}
            />
            <button type="submit" disabled={!draft.trim() || !conversation || sending}>
              {sending ? '…' : '↑'}
            </button>
          </form>
          <p>＋ 上传文件 · Enter 发送 · Shift + Enter 换行 · 执行路径不包含模型隐藏思维</p>
        </div>
      </section>

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

            <label>
              <span>选择执行模型</span>
              <select
                value={settingsModelId}
                onChange={(event) => {
                  setSettingsModelId(event.target.value)
                  setProviderMode(event.target.value)
                  setProviderTest(null)
                }}
              >
                {modelCatalog?.models.map((model) => (
                  <option key={model.id} value={model.id}>
                    {model.displayName} · {model.providerId}
                  </option>
                ))}
              </select>
            </label>

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
