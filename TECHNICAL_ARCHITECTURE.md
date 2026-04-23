# AI Edge Gallery 自动化工作流 - 技术架构文档

| 项目 | AI Edge Gallery Workflow 技术架构 |
|------|----------------------------------|
| 版本 | 1.0 |
| 日期 | 2026-04-23 |

---

## 1. 系统架构总览

### 1.1 分层架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │   Workflow  │  │   Workflow  │  │   Trigger   │  │   Result    │   │
│  │   Editor    │  │   List      │  │   Config    │  │   Viewer    │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                           Domain Layer                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │  Workflow   │  │   Action    │  │  Variable   │  │   Trigger   │   │
│  │   Model     │  │   Model     │  │   Model     │  │   Model     │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                          Execution Layer                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │  Executor   │  │   Action    │  │  Condition  │  │   Loop      │   │
│  │  Engine     │  │  Registry   │  │   Evaluator │  │   Handler   │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                           Service Layer                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │  Workflow   │  │  Schedule   │  │    LLM      │  │   Device    │   │
│  │  Service    │  │  Service    │  │   Bridge    │  │   Service   │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                         Infrastructure Layer                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │    Room     │  │   Work      │  │  Fused      │  │   File      │   │
│  │  Database   │  │  Manager    │  │  Location   │  │   Storage   │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 模块依赖关系

```
                    ┌─────────────────┐
                    │   UI (Compose)  │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   ViewModel     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼─────┐ ┌──────▼─────┐ ┌──────▼──────┐
     │   Workflow   │ │  Schedule  │ │   Trigger   │
     │   Service    │ │  Service   │ │   Service   │
     └──────┬───────┘ └──────┬─────┘ └──────┬──────┘
            │                │              │
            └────────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │  Executor Engine │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼─────┐ ┌──────▼─────┐ ┌──────▼──────┐
     │   Action     │ │  Variable  │ │   Condition │
     │   Registry   │ │   Store    │ │   Evaluator │
     └──────┬───────┘ └────────────┘ └─────────────┘
            │
     ┌──────▼───────┐
     │  LLM Bridge  │
     │  (LiteRT-LM) │
     └──────────────┘
```

---

## 2. 数据模型设计

### 2.1 核心实体关系图

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│    Workflow     │       │    Trigger     │       │    Action       │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id: Long (PK)  │──┐    │ id: Long (PK)  │       │ id: Long (PK)  │
│ name: String   │  │    │ workflowId: FK │──┐    │ workflowId: FK │──┐
│ description    │  │    │ type: Enum     │  │    │ order: Int      │  │
│ version: Int   │  │    │ config: JSON   │  │    │ type: Enum      │  │
│ isEnabled      │  │    │ isEnabled      │  │    │ config: JSON    │  │
│ createdAt      │  │    └─────────────────┘  │    │ outputVar       │  │
│ updatedAt      │  │                        │    └─────────────────┘  │
└─────────────────┘  │                        │            │
         │           │                        │            │
         │           │                        │            │
         ▼           ▼                        ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ActionConnection                          │
├─────────────────────────────────────────────────────────────────┤
│ fromActionId: Long (FK)  │  toActionId: Long (FK)               │
│ condition: String?      │  isConditionalBranch: Boolean        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │    Variable      │
                     ├─────────────────┤
                     │ id: Long (PK)   │
                     │ workflowId: FK │
                     │ name: String    │
                     │ type: Enum      │
                     │ value: Any?     │
                     │ scope: Enum     │
                     └─────────────────┘
```

### 2.2 Room Database Schema

```kotlin
// 2.2.1 Workflow 表
@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val version: Int = 1,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val runCount: Int = 0
)

// 2.2.2 Trigger 表
@Entity(
    tableName = "triggers",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val workflowId: Long,
    val type: String, // MANUAL, TIMER, INTERVAL, LOCATION, DEVICE_EVENT, SENSOR
    val config: String, // JSON 配置
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

// 2.2.3 Action 表
@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val workflowId: Long,
    val orderIndex: Int,
    val type: String, // LLM_ASK, TEXT_INPUT, TAKE_PHOTO, NOTIFICATION, etc.
    val config: String, // JSON 配置
    val outputVariableName: String?,
    val createdAt: Long = System.currentTimeMillis()
)

// 2.2.4 ActionConnection 表 (流程连接)
@Entity(
    tableName = "action_connections",
    primaryKeys = ["fromActionId", "toActionId"],
    foreignKeys = [
        ForeignKey(
            entity = ActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromActionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["toActionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActionConnectionEntity(
    @ColumnInfo(index = true)
    val fromActionId: Long,
    @ColumnInfo(index = true)
    val toActionId: Long,
    val conditionExpression: String?, // e.g., "output contains 'success'"
    val branchType: String = "DEFAULT" // DEFAULT, IF_TRUE, IF_FALSE
)

// 2.2.5 Variable 表
@Entity(
    tableName = "variables",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VariableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val workflowId: Long,
    val name: String,
    val type: String, // TEXT, NUMBER, BOOLEAN, IMAGE, AUDIO, LIST, DICT
    val defaultValue: String?, // JSON 序列化
    val scope: String = "WORKFLOW" // WORKFLOW, GLOBAL
)

// 2.2.6 WorkflowExecutionLog 表 (执行日志)
@Entity(
    tableName = "workflow_execution_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkflowExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val workflowId: Long,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String, // RUNNING, COMPLETED, FAILED, CANCELLED
    val triggerType: String,
    val errorMessage: String?
)
```

### 2.3 领域模型 (Domain Models)

```kotlin
// 2.3.1 Workflow 领域模型
data class Workflow(
    val id: Long = 0,
    val name: String,
    val description: String?,
    val version: Int = 1,
    val isEnabled: Boolean = true,
    val trigger: Trigger,
    val actions: List<Action>,
    val connections: List<ActionConnection>,
    val variables: List<Variable>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val runCount: Int = 0
)

// 2.3.2 Trigger 领域模型
sealed class Trigger {
    abstract val id: Long
    abstract val isEnabled: Boolean

    data class Manual(
        override val id: Long = 0,
        override val isEnabled: Boolean = true
    ) : Trigger()

    data class Timer(
        override val id: Long = 0,
        override val isEnabled: Boolean = true,
        val time: String, // "08:00"
        val daysOfWeek: List<Int> = listOf(1,2,3,4,5) // Mon-Fri
    ) : Trigger()

    data class Interval(
        override val id: Long = 0,
        override val isEnabled: Boolean = true,
        val intervalMinutes: Int,
        val startTime: String? = null,
        val endTime: String? = null
    ) : Trigger()

    data class Location(
        override val id: Long = 0,
        override val isEnabled: Boolean = true,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float = 100f,
        val triggerOn: LocationTriggerType = LocationTriggerType.ARRIVE
    ) : Trigger()

    data class DeviceEvent(
        override val id: Long = 0,
        override val isEnabled: Boolean = true,
        val eventType: DeviceEventType
    ) : Trigger()

    data class Sensor(
        override val id: Long = 0,
        override val isEnabled: Boolean = true,
        val sensorType: SensorType,
        val condition: SensorCondition
    ) : Trigger()
}

enum class LocationTriggerType { ARRIVE, LEAVE }
enum class DeviceEventType { USB_CONNECTED, USB_DISCONNECTED, CHARGING_STARTED, CHARGING_STOPPED, BOOT_COMPLETED }
enum class SensorType { SHAKE, ORIENTATION_CHANGE, PROXIMITY }

// 2.3.3 Action 领域模型
sealed class Action {
    abstract val id: Long
    abstract val orderIndex: Int
    abstract val outputVariableName: String?

    // AI Actions
    data class LlmAsk(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "llm_output",
        val modelId: String,
        val systemInstruction: String,
        val inputBinding: String, // 绑定输入变量
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val maxTokens: Int = 1024
    ) : Action()

    data class LlmTranslate(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "translated_text",
        val inputText: String,
        val targetLanguage: String
    ) : Action()

    data class LlmSummarize(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "summary",
        val inputText: String,
        val maxLength: Int = 100
    ) : Action()

    // Input Actions
    data class TakePhoto(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "photo",
        val cameraType: CameraType = CameraType.BACK
    ) : Action()

    data class PickImage(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "image",
        val source: ImageSource = ImageSource.GALLERY
    ) : Action()

    data class RecordAudio(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "audio",
        val maxDurationSeconds: Int = 60
    ) : Action()

    data class TextInput(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "user_input",
        val prompt: String,
        val defaultValue: String = ""
    ) : Action()

    // Output Actions
    data class ShowResult(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val title: String,
        val inputBinding: String
    ) : Action()

    data class SendNotification(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val title: String,
        val bodyBinding: String
    ) : Action()

    data class CopyToClipboard(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val textBinding: String
    ) : Action()

    // Device Actions
    data class GetLocation(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "location",
        val accuracy: LocationAccuracy = LocationAccuracy.HIGH
    ) : Action()

    data class GetCurrentTime(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "current_time",
        val format: String = "yyyy-MM-dd HH:mm:ss"
    ) : Action()

    // Control Actions
    data class ConditionBranch(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val condition: Condition,
        val trueBranchActions: List<Action>,
        val falseBranchActions: List<Action>
    ) : Action()

    data class Repeat(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val times: Int,
        val actions: List<Action>
    ) : Action()

    data class Wait(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val seconds: Int
    ) : Action()

    // Variable Actions
    data class SetVariable(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val variableName: String,
        val valueBinding: String
    ) : Action()

    data class GetVariable(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = null,
        val variableName: String
    ) : Action()

    // API Actions
    data class HttpRequest(
        override val id: Long = 0,
        override val orderIndex: Int,
        override val outputVariableName: String? = "http_response",
        val url: String,
        val method: HttpMethod = HttpMethod.GET,
        val headers: Map<String, String> = emptyMap(),
        val bodyBinding: String? = null
    ) : Action()
}

enum class CameraType { FRONT, BACK }
enum class ImageSource { GALLERY, FILES }
enum class LocationAccuracy { LOW, MEDIUM, HIGH }
enum class HttpMethod { GET, POST, PUT, DELETE }

// 2.3.4 Variable 领域模型
data class Variable(
    val id: Long = 0,
    val name: String,
    val type: VariableType,
    val defaultValue: Any? = null,
    val scope: VariableScope = VariableScope.WORKFLOW
)

enum class VariableType { TEXT, NUMBER, BOOLEAN, IMAGE, AUDIO, LIST, DICT }
enum class VariableScope { WORKFLOW, GLOBAL }

// 2.3.5 Condition 模型
data class Condition(
    val leftOperand: String,
    val operator: ConditionOperator,
    val rightOperand: String
)

enum class ConditionOperator {
    EQUALS, NOT_EQUALS,
    CONTAINS, NOT_CONTAINS,
    GREATER_THAN, LESS_THAN,
    IS_EMPTY, IS_NOT_EMPTY,
    LLM_JUDGE // LLM 判断
}

// 2.3.6 ActionConnection 领域模型
data class ActionConnection(
    val fromActionId: Long,
    val toActionId: Long,
    val conditionExpression: String? = null,
    val branchType: BranchType = BranchType.DEFAULT
)

enum class BranchType { DEFAULT, IF_TRUE, IF_FALSE }
```

---

## 3. 工作流执行引擎设计

### 3.1 执行器核心接口

```kotlin
// 3.1.1 执行引擎接口
interface WorkflowExecutor {
    suspend fun execute(workflow: Workflow, context: ExecutionContext): ExecutionResult
    suspend fun cancel(executionId: String)
    fun getExecutionState(executionId: String): ExecutionState?
}

data class ExecutionContext(
    val executionId: String,
    val triggerType: String,
    val triggerData: Map<String, Any?>,
    val variables: MutableMap<String, Any>,
    val actionOutputs: MutableMap<Long, Any>,
    val startTime: Long = System.currentTimeMillis()
)

sealed class ExecutionResult {
    data class Success(
        val output: Any?,
        val durationMs: Long,
        val actionOutputs: Map<Long, Any>
    ) : ExecutionResult()

    data class Failure(
        val error: String,
        val failedActionId: Long?,
        val durationMs: Long
    ) : ExecutionResult()

    data class Cancelled(
        val completedActions: Int,
        val totalActions: Int,
        val durationMs: Long
    ) : ExecutionResult()
}

// 3.1.2 执行状态
data class ExecutionState(
    val executionId: String,
    val workflowId: Long,
    val status: ExecutionStatus,
    val currentActionIndex: Int,
    val currentAction: Action?,
    val variables: Map<String, Any>,
    val actionLogs: List<ActionLog>,
    val startTime: Long,
    val errorMessage: String?
)

enum class ExecutionStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

data class ActionLog(
    val actionId: Long,
    val actionType: String,
    val input: Map<String, Any>,
    val output: Any?,
    val status: ActionStatus,
    val startTime: Long,
    val endTime: Long?,
    val errorMessage: String?
)

enum class ActionStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }
```

### 3.2 执行器实现

```kotlin
class WorkflowExecutorImpl(
    private val actionRegistry: ActionRegistry,
    private val conditionEvaluator: ConditionEvaluator,
    private val variableStore: VariableStore,
    private val llmBridge: LlmBridge
) : WorkflowExecutor {

    private val activeExecutions = ConcurrentHashMap<String, ExecutionState>()

    override suspend fun execute(
        workflow: Workflow,
        context: ExecutionContext
    ): ExecutionResult {
        val executionId = context.executionId

        // 初始化执行状态
        activeExecutions[executionId] = ExecutionState(
            executionId = executionId,
            workflowId = workflow.id,
            status = ExecutionStatus.RUNNING,
            currentActionIndex = 0,
            currentAction = null,
            variables = context.variables,
            actionLogs = emptyList(),
            startTime = System.currentTimeMillis(),
            errorMessage = null
        )

        try {
            // 按顺序执行 Actions
            val sortedActions = workflow.actions.sortedBy { it.orderIndex }
            val actionOutputMap = mutableMapOf<Long, Any>()

            for ((index, action) in sortedActions.withIndex()) {
                // 更新执行状态
                updateExecutionState(executionId) {
                    it.copy(currentActionIndex = index, currentAction = action)
                }

                // 绑定输入
                val inputBinding = resolveInputBinding(action, actionOutputMap, context.variables)

                // 检查是否为条件分支
                if (action is Action.ConditionBranch) {
                    val result = executeConditionBranch(action, inputBinding, actionOutputMap, context)
                    if (result is ExecutionResult.Failure) {
                        return result
                    }
                } else {
                    // 执行 Action
                    val actionResult = executeAction(action, inputBinding, context)
                    when (actionResult) {
                        is ActionResult.Success -> {
                            actionOutputMap[action.id] = actionResult.output
                            context.actionOutputs[action.id] = actionResult.output
                            if (action.outputVariableName != null) {
                                context.variables[action.outputVariableName] = actionResult.output
                            }
                        }
                        is ActionResult.Failure -> {
                            return ExecutionResult.Failure(
                                error = actionResult.error,
                                failedActionId = action.id,
                                durationMs = System.currentTimeMillis() - context.startTime
                            )
                        }
                        is ActionResult.Skipped -> {
                            // 跳过，不保存输出
                        }
                    }
                }

                // 记录 Action 日志
                logActionExecution(executionId, action, ActionStatus.COMPLETED)
            }

            return ExecutionResult.Success(
                output = actionOutputMap.values.lastOrNull(),
                durationMs = System.currentTimeMillis() - context.startTime,
                actionOutputs = actionOutputMap
            )

        } catch (e: Exception) {
            return ExecutionResult.Failure(
                error = e.message ?: "Unknown error",
                failedActionId = null,
                durationMs = System.currentTimeMillis() - context.startTime
            )
        } finally {
            activeExecutions.remove(executionId)
        }
    }

    private suspend fun executeAction(
        action: Action,
        inputBinding: Map<String, Any>,
        context: ExecutionContext
    ): ActionResult {
        val executor = actionRegistry.getExecutor(action::class)
        return executor.execute(action, inputBinding, context)
    }
}

// 3.1.3 Action 结果
sealed class ActionResult {
    data class Success(val output: Any) : ActionResult()
    data class Failure(val error: String) : ActionResult()
    object Skipped : ActionResult()
}
```

### 3.3 条件执行器

```kotlin
class ConditionEvaluator(
    private val llmBridge: LlmBridge
) {
    fun evaluate(condition: Condition, variables: Map<String, Any>): Boolean {
        val leftValue = resolveValue(condition.leftOperand, variables)
        val rightValue = resolveValue(condition.rightOperand, variables)

        return when (condition.operator) {
            ConditionOperator.EQUALS -> leftValue == rightValue
            ConditionOperator.NOT_EQUALS -> leftValue != rightValue
            ConditionOperator.CONTAINS -> leftValue?.toString()?.contains(rightValue.toString()) == true
            ConditionOperator.NOT_CONTAINS -> !leftValue?.toString()?.contains(rightValue.toString())!! }
            ConditionOperator.GREATER_THAN -> (leftValue as? Number)?.toDouble()!! > (rightValue as? Number)?.toDouble()!!
            ConditionOperator.LESS_THAN -> (leftValue as? Number)?.toDouble()!! < (rightValue as? Number)?.toDouble()!!
            ConditionOperator.IS_EMPTY -> leftValue == null || leftValue.toString().isEmpty()
            ConditionOperator.IS_NOT_EMPTY -> leftValue != null && leftValue.toString().isNotEmpty()
            ConditionOperator.LLM_JUDGE -> evaluateWithLlm(condition, variables)
        }
    }

    private suspend fun evaluateWithLlm(
        condition: Condition,
        variables: Map<String, Any>
    ): Boolean {
        val prompt = buildString {
            appendLine("判断以下条件是否为真：")
            appendLine("条件: ${condition.leftOperand} ${condition.operator} ${condition.rightOperand}")
            appendLine("变量上下文: $variables")
            appendLine("请只回答 'true' 或 'false'")
        }

        val result = llmBridge.generate(prompt, maxTokens = 10)
        return result.trim().equals("true", ignoreCase = true)
    }
}
```

### 3.4 Action 注册表

```kotlin
interface ActionRegistry {
    fun register(actionType: KClass<out Action>, executor: ActionExecutor<out Action>)
    fun getExecutor(actionType: KClass<out Action>): ActionExecutor<out Action>
    fun getAllActionTypes(): List<KClass<out Action>>
}

interface ActionExecutor<A : Action> {
    suspend fun execute(action: A, input: Map<String, Any>, context: ExecutionContext): ActionResult
    fun describeInputPorts(action: A): List<InputPort>
    fun describeOutputPort(action: A): OutputPort?
}

// LLM Ask Action 执行器示例
class LlmAskActionExecutor(
    private val llmBridge: LlmBridge
) : ActionExecutor<Action.LlmAsk> {

    override suspend fun execute(
        action: Action.LlmAsk,
        input: Map<String, Any>,
        context: ExecutionContext
    ): ActionResult {
        return try {
            val inputText = input[action.inputBinding]?.toString()
                ?: throw IllegalArgumentException("Input binding '${action.inputBinding}' not found")

            val systemInstruction = action.systemInstruction
            val fullPrompt = if (systemInstruction.isNotEmpty()) {
                "$systemInstruction\n\nUser input: $inputText"
            } else {
                inputText
            }

            val output = llmBridge.generate(
                prompt = fullPrompt,
                temperature = action.temperature,
                topK = action.topK,
                maxTokens = action.maxTokens
            )

            ActionResult.Success(output)
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "LLM execution failed")
        }
    }

    override fun describeInputPorts(action: Action.LlmAsk): List<InputPort> = listOf(
        InputPort(name = action.inputBinding, type = VariableType.TEXT)
    )

    override fun describeOutputPort(action: Action.LlmAsk): OutputPort? =
        OutputPort(name = action.outputVariableName ?: "output", type = VariableType.TEXT)
}

// Take Photo Action 执行器
class TakePhotoActionExecutor(
    private val cameraController: CameraController
) : ActionExecutor<Action.TakePhoto> {

    override suspend fun execute(
        action: Action.TakePhoto,
        input: Map<String, Any>,
        context: ExecutionContext
    ): ActionResult {
        return try {
            val image = cameraController.takePhoto(
                cameraType = action.cameraType
            )
            ActionResult.Success(image)
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Failed to take photo")
        }
    }
}

// Send Notification Action 执行器
class SendNotificationActionExecutor(
    private val notificationManager: NotificationManager
) : ActionExecutor<Action.SendNotification> {

    override suspend fun execute(
        action: Action.SendNotification,
        input: Map<String, Any>,
        context: ExecutionContext
    ): ActionResult {
        return try {
            val body = input[action.bodyBinding]?.toString() ?: ""
            notificationManager.send(
                title = action.title,
                body = body
            )
            ActionResult.Success(Unit)
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Failed to send notification")
        }
    }
}
```

---

## 4. LLM Bridge 设计

### 4.1 接口定义

```kotlin
interface LlmBridge {
    suspend fun generate(
        prompt: String,
        temperature: Float = 0.7f,
        topK: Int = 40,
        maxTokens: Int = 1024,
        systemInstruction: String? = null,
        images: List<ByteArray> = emptyList(),
        audio: ByteArray? = null
    ): String

    suspend fun generateStream(
        prompt: String,
        onToken: (String) -> Unit,
        temperature: Float = 0.7f,
        topK: Int = 40,
        maxTokens: Int = 1024,
        systemInstruction: String? = null
    ): suspend () -> Unit

    fun getAvailableModels(): List<LlmModel>
}

data class LlmModel(
    val id: String,
    val name: String,
    val maxTokens: Int,
    val supportsImages: Boolean,
    val supportsAudio: Boolean
)
```

### 4.2 LiteRT-LM 实现

```kotlin
class LiteRtLlmBridge(
    private val modelManager: ModelManager,
    private val llmChatModelHelper: LlmChatModelHelper
) : LlmBridge {

    private val defaultModelId = "gemma-4-it"

    override suspend fun generate(
        prompt: String,
        temperature: Float,
        topK: Int,
        maxTokens: Int,
        systemInstruction: String?,
        images: List<ByteArray>,
        audio: ByteArray?
    ): String = suspendCoroutine { continuation ->
        val model = modelManager.getModel(defaultModelId)

        llmChatModelHelper.initialize(
            context = context,
            model = model,
            supportImage = images.isNotEmpty(),
            supportAudio = audio != null,
            onDone = { _ ->
                val input = buildLlmInput(prompt, systemInstruction, images, audio)
                llmChatModelHelper.runInference(
                    input = input,
                    onToken = { /* ignore streaming */ },
                    onDone = { output ->
                        continuation.resumeWith(Result.success(output))
                    },
                    onError = { error ->
                        continuation.resumeWith(Result.failure(Exception(error)))
                    }
                )
            }
        )
    }

    override suspend fun generateStream(
        prompt: String,
        onToken: (String) -> Unit,
        temperature: Float,
        topK: Int,
        maxTokens: Int,
        systemInstruction: String?
    ): suspend () -> Unit {
        val model = modelManager.getModel(defaultModelId)
        var cancelToken: (() -> Unit)? = null

        llmChatModelHelper.initialize(
            context = context,
            model = model,
            supportImage = false,
            supportAudio = false,
            onDone = { _ ->
                val input = buildLlmInput(prompt, systemInstruction, emptyList(), null)
                llmChatModelHelper.runInference(
                    input = input,
                    onToken = { token -> onToken(token) },
                    onDone = { /* completed */ },
                    onError = { /* error */ }
                )
            }
        )

        return { cancelToken?.invoke() }
    }
}
```

---

## 5. 调度服务设计

### 5.1 定时触发器实现

```kotlin
class ScheduleService(
    private val workManager: WorkManager,
    private val workflowRepository: WorkflowRepository
) {

    fun scheduleWorkflow(workflow: Workflow) {
        val trigger = workflow.trigger

        when (trigger) {
            is Trigger.Timer -> scheduleTimerTrigger(workflow, trigger)
            is Trigger.Interval -> scheduleIntervalTrigger(workflow, trigger)
            is Trigger.Location -> scheduleLocationTrigger(workflow, trigger)
            is Trigger.DeviceEvent -> registerDeviceEventTrigger(workflow, trigger)
            is Trigger.Sensor -> registerSensorTrigger(workflow, trigger)
            is Trigger.Manual -> { /* 无需调度 */ }
        }
    }

    private fun scheduleTimerTrigger(workflow: Workflow, trigger: Trigger.Timer) {
        val timeParts = trigger.time.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        val workRequest = PeriodicWorkRequestBuilder<WorkflowWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(calculateInitialDelay(hour, minute), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("workflow_${workflow.id}")
            .addTag("timer")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "workflow_timer_${workflow.id}",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleIntervalTrigger(workflow: Workflow, trigger: Trigger.Interval) {
        val workRequest = PeriodicWorkRequestBuilder<WorkflowWorker>(
            trigger.intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .addTag("workflow_${workflow.id}")
            .addTag("interval")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "workflow_interval_${workflow.id}",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    fun cancelWorkflowSchedule(workflowId: Long) {
        workManager.cancelAllWorkByTag("workflow_$workflowId")
    }
}

// WorkManager Worker
class WorkflowWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val workflowExecutor: WorkflowExecutor
    private val workflowRepository: WorkflowRepository

    override suspend fun doWork(): Result {
        val workflowId = inputData.getLong("workflow_id", -1)
        if (workflowId == -1L) return Result.failure()

        val workflow = workflowRepository.getById(workflowId) ?: return Result.failure()

        val context = ExecutionContext(
            executionId = UUID.randomUUID().toString(),
            triggerType = "SCHEDULED",
            triggerData = emptyMap(),
            variables = mutableMapOf()
        )

        val result = workflowExecutor.execute(workflow, context)

        return when (result) {
            is ExecutionResult.Success -> Result.success()
            is ExecutionResult.Failure -> Result.retry()
            is ExecutionResult.Cancelled -> Result.success()
        }
    }
}
```

---

## 6. API 设计

### 6.1 Workflow Management API

```kotlin
// 6.1.1 创建工作流
POST /api/v1/workflows
Content-Type: application/json

Request:
{
  "name": "拍照分析",
  "description": "拍照并用 LLM 分析",
  "trigger": {
    "type": "MANUAL"
  },
  "actions": [
    {
      "type": "TAKE_PHOTO",
      "orderIndex": 0,
      "outputVariableName": "photo",
      "config": {
        "cameraType": "BACK"
      }
    },
    {
      "type": "LLM_ASK",
      "orderIndex": 1,
      "outputVariableName": "analysis",
      "config": {
        "modelId": "gemma-4",
        "systemInstruction": "分析这张图片，描述其中的主要内容",
        "inputBinding": "photo",
        "temperature": 0.7,
        "maxTokens": 500
      }
    },
    {
      "type": "SEND_NOTIFICATION",
      "orderIndex": 2,
      "config": {
        "title": "分析结果",
        "bodyBinding": "analysis"
      }
    }
  ],
  "variables": [
    {
      "name": "photo",
      "type": "IMAGE",
      "scope": "WORKFLOW"
    },
    {
      "name": "analysis",
      "type": "TEXT",
      "scope": "WORKFLOW"
    }
  ]
}

Response (201 Created):
{
  "id": 1,
  "name": "拍照分析",
  "description": "拍照并用 LLM 分析",
  "version": 1,
  "isEnabled": true,
  "createdAt": "2026-04-23T10:00:00Z",
  "updatedAt": "2026-04-23T10:00:00Z"
}

// 6.1.2 获取工作流列表
GET /api/v1/workflows?page=0&size=20&enabled=true

Response (200 OK):
{
  "content": [
    {
      "id": 1,
      "name": "拍照分析",
      "description": "拍照并用 LLM 分析",
      "triggerType": "MANUAL",
      "actionCount": 3,
      "isEnabled": true,
      "lastRunAt": "2026-04-23T09:30:00Z",
      "runCount": 15
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "currentPage": 0
}

// 6.1.3 获取单个工作流
GET /api/v1/workflows/{id}

Response (200 OK):
{
  "id": 1,
  "name": "拍照分析",
  "description": "拍照并用 LLM 分析",
  "trigger": { ... },
  "actions": [ ... ],
  "variables": [ ... ],
  "connections": [ ... ]
}

// 6.1.4 更新工作流
PUT /api/v1/workflows/{id}
Content-Type: application/json

Request: { ... same as create ... }

Response (200 OK):
{
  "id": 1,
  "name": "拍照分析 (更新版)",
  "version": 2,
  ...
}

// 6.1.5 删除工作流
DELETE /api/v1/workflows/{id}

Response (204 No Content)

// 6.1.6 启用/禁用工作流
PATCH /api/v1/workflows/{id}/enabled
Content-Type: application/json

Request:
{ "isEnabled": true }

Response (200 OK):
{
  "id": 1,
  "isEnabled": true
}
```

### 6.2 Workflow Execution API

```kotlin
// 6.2.1 手动执行工作流
POST /api/v1/workflows/{id}/execute

Request:
{
  "inputVariables": {
    "user_input": "Hello"
  }
}

Response (202 Accepted):
{
  "executionId": "exec-uuid-123",
  "status": "RUNNING",
  "workflowId": 1,
  "startedAt": "2026-04-23T10:00:00Z"
}

// 6.2.2 获取执行状态
GET /api/v1/workflows/{id}/executions/{executionId}

Response (200 OK):
{
  "executionId": "exec-uuid-123",
  "workflowId": 1,
  "status": "COMPLETED",
  "currentActionIndex": 3,
  "totalActions": 3,
  "startTime": "2026-04-23T10:00:00Z",
  "completedAt": "2026-04-23T10:00:15Z",
  "durationMs": 15000,
  "output": {
    "finalResult": "分析完成：照片中有一只猫"
  }
}

// 6.2.3 取消执行
POST /api/v1/workflows/{id}/executions/{executionId}/cancel

Response (200 OK):
{
  "executionId": "exec-uuid-123",
  "status": "CANCELLED"
}

// 6.2.4 获取执行历史
GET /api/v1/workflows/{id}/executions?page=0&size=10

Response (200 OK):
{
  "content": [
    {
      "executionId": "exec-uuid-123",
      "status": "COMPLETED",
      "triggerType": "MANUAL",
      "startedAt": "2026-04-23T10:00:00Z",
      "completedAt": "2026-04-23T10:00:15Z",
      "durationMs": 15000
    }
  ],
  "totalElements": 50,
  "totalPages": 5
}
```

### 6.3 Action Templates API

```kotlin
// 6.3.1 获取可用 Action 类型列表
GET /api/v1/actions/types

Response (200 OK):
{
  "categories": [
    {
      "name": "AI",
      "actions": [
        {
          "type": "LLM_ASK",
          "name": "问 LLM",
          "description": "向大语言模型提问或发送任务",
          "inputPorts": [
            { "name": "input", "type": "TEXT", "required": true, "description": "输入文本或变量" }
          ],
          "outputPort": { "name": "output", "type": "TEXT" },
          "configFields": [
            { "name": "modelId", "type": "STRING", "required": false, "default": "gemma-4" },
            { "name": "systemInstruction", "type": "STRING", "required": false },
            { "name": "temperature", "type": "FLOAT", "required": false, "default": 0.7 },
            { "name": "maxTokens", "type": "INT", "required": false, "default": 1024 }
          ]
        },
        {
          "type": "LLM_TRANSLATE",
          "name": "翻译",
          "description": "使用 LLM 翻译文本",
          ...
        }
      ]
    },
    {
      "name": "Input",
      "actions": [
        { "type": "TAKE_PHOTO", "name": "拍照", ... },
        { "type": "PICK_IMAGE", "name": "选择照片", ... },
        { "type": "RECORD_AUDIO", "name": "录音", ... },
        { "type": "TEXT_INPUT", "name": "文本输入", ... }
      ]
    },
    {
      "name": "Output",
      "actions": [
        { "type": "SHOW_RESULT", "name": "显示结果", ... },
        { "type": "SEND_NOTIFICATION", "name": "发送通知", ... },
        { "type": "COPY_TO_CLIPBOARD", "name": "复制到剪贴板", ... }
      ]
    },
    {
      "name": "Control",
      "actions": [
        { "type": "CONDITION_BRANCH", "name": "条件分支", ... },
        { "type": "REPEAT", "name": "重复", ... },
        { "type": "WAIT", "name": "等待", ... }
      ]
    }
  ]
}
```

---

## 7. 工作流导入/导出格式

### 7.1 JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "AI Edge Gallery Workflow Schema",
  "version": "1.0.0",
  "definitions": {
    "workflow": {
      "type": "object",
      "required": ["name", "trigger", "actions"],
      "properties": {
        "schemaVersion": { "type": "string", "const": "1.0.0" },
        "name": { "type": "string", "maxLength": 100 },
        "description": { "type": "string", "maxLength": 500 },
        "trigger": { "$ref": "#/definitions/trigger" },
        "actions": {
          "type": "array",
          "minItems": 1,
          "items": { "$ref": "#/definitions/action" }
        },
        "variables": {
          "type": "array",
          "items": { "$ref": "#/definitions/variable" }
        },
        "connections": {
          "type": "array",
          "items": { "$ref": "#/definitions/connection" }
        }
      }
    },
    "trigger": {
      "oneOf": [
        { "$ref": "#/definitions/triggers/manual" },
        { "$ref": "#/definitions/triggers/timer" },
        { "$ref": "#/definitions/triggers/interval" },
        { "$ref": "#/definitions/triggers/location" }
      ]
    },
    "action": {
      "oneOf": [
        { "$ref": "#/definitions/actions/llm_ask" },
        { "$ref": "#/definitions/actions/take_photo" },
        { "$ref": "#/definitions/actions/notification" }
      ]
    },
    "variable": {
      "type": "object",
      "required": ["name", "type"],
      "properties": {
        "name": { "type": "string" },
        "type": { "enum": ["TEXT", "NUMBER", "BOOLEAN", "IMAGE", "AUDIO", "LIST", "DICT"] },
        "defaultValue": { }
      }
    }
  }
}
```

### 7.2 导出示例

```json
{
  "schemaVersion": "1.0.0",
  "exportedAt": "2026-04-23T10:00:00Z",
  "appVersion": "1.0.12",
  "workflow": {
    "name": "拍照分析",
    "description": "拍照并用 LLM 分析图片内容",
    "trigger": {
      "type": "MANUAL"
    },
    "actions": [
      {
        "id": "action-1",
        "type": "TAKE_PHOTO",
        "config": {
          "cameraType": "BACK"
        },
        "outputVariable": "photo"
      },
      {
        "id": "action-2",
        "type": "LLM_ASK",
        "config": {
          "modelId": "gemma-4",
          "systemInstruction": "分析这张图片，描述其中的主要内容",
          "temperature": 0.7,
          "maxTokens": 500
        },
        "inputMapping": {
          "input": "var:photo"
        },
        "outputVariable": "analysis"
      },
      {
        "id": "action-3",
        "type": "SEND_NOTIFICATION",
        "config": {
          "title": "分析结果"
        },
        "inputMapping": {
          "body": "var:analysis"
        }
      }
    ],
    "connections": [
      { "from": "action-1", "to": "action-2" },
      { "from": "action-2", "to": "action-3" }
    ],
    "variables": [
      { "name": "photo", "type": "IMAGE" },
      { "name": "analysis", "type": "TEXT" }
    ]
  }
}
```

---

## 8. UI 组件架构

### 8.1 Screen 结构

```
WorkflowScreens/
├── WorkflowListScreen.kt          # 工作流列表
├── WorkflowEditorScreen.kt         # 工作流编辑器 (主界面)
├── TriggerConfigSheet.kt           # 触发器配置 Bottom Sheet
├── ActionConfigSheet.kt            # Action 配置 Bottom Sheet
├── ActionPickerSheet.kt            # Action 选择器
├── VariablePanelSheet.kt           # 变量面板
├── ConditionEditorSheet.kt          # 条件编辑器
├── WorkflowExecutionScreen.kt      # 执行画面
├── ExecutionResultScreen.kt        # 执行结果展示
└── WorkflowImportScreen.kt        # 导入/导出

WorkflowEditor/
├── WorkflowEditorState.kt          # Editor ViewModel State
├── WorkflowEditorViewModel.kt      # Editor ViewModel
├── components/
│   ├── WorkflowCanvas.kt            # 可视化画布
│   ├── ActionNode.kt                # Action 节点组件
│   ├── TriggerNode.kt               # Trigger 节点组件
│   ├── ConnectionLine.kt            # 连接线组件
│   ├── ActionConfigCard.kt          # Action 配置卡片
│   ├── VariableChip.kt              # 变量标签
│   └── DraggableActionItem.kt       # 可拖拽的 Action 项
└── editor/
    ├── CanvasInteractionHandler.kt  # 画布交互处理
    ├── NodeDragHandler.kt           # 节点拖拽处理
    └── ConnectionDragHandler.kt     # 连接拖拽处理
```

### 8.2 ViewModel 状态设计

```kotlin
// WorkflowEditorState
data class WorkflowEditorState(
    val workflow: Workflow,
    val selectedActionId: Long?,
    val selectedConnectionId: Pair<Long, Long>?,
    val isDragging: Boolean,
    val dragState: DragState?,
    val zoomLevel: Float,
    val panOffset: Offset,
    val isModified: Boolean,
    val isSaving: Boolean,
    val isExecuting: Boolean,
    val executionState: ExecutionState?,
    val error: String?
)

sealed class DragState {
    data class ActionDrag(
        val actionType: String,
        val offset: Offset
    ) : DragState()

    data class ConnectionDrag(
        val fromActionId: Long,
        val currentOffset: Offset
    ) : DragState()

    data class NodeDrag(
        val actionId: Long,
        val startOffset: Offset,
        val currentOffset: Offset
    ) : DragState()
}

// WorkflowEditorViewModel
class WorkflowEditorViewModel @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val workflowExecutor: WorkflowExecutor,
    private val actionRegistry: ActionRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(WorkflowEditorState(...))
    val state: StateFlow<WorkflowEditorState> = _state.asStateFlow()

    fun loadWorkflow(workflowId: Long) { ... }
    fun createNewWorkflow() { ... }

    fun addAction(actionType: String, position: Offset) { ... }
    fun removeAction(actionId: Long) { ... }
    fun updateActionConfig(actionId: Long, config: Map<String, Any>) { ... }
    fun moveAction(actionId: Long, newPosition: Offset) { ... }

    fun connectActions(fromActionId: Long, toActionId: Long) { ... }
    fun disconnectActions(fromActionId: Long, toActionId: Long) { ... }

    fun setTrigger(trigger: Trigger) { ... }

    fun addVariable(variable: Variable) { ... }
    fun removeVariable(variableName: String) { ... }

    fun saveWorkflow(): Flow<Result<Workflow>> { ... }
    fun executeWorkflow() { ... }
    fun cancelExecution() { ... }

    fun undo() { ... }
    fun redo() { ... }
}
```

---

## 9. 状态管理

### 9.1 UI State 状态机

```
┌─────────────┐
│   LOADING   │
└──────┬──────┘
       │ workflow loaded
       ▼
┌─────────────┐    edit started    ┌─────────────┐
│    IDLE    │──────────────────▶  │   EDITING   │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │ save clicked                save clicked
       ▼ (success)                        ▼ (success)
┌─────────────┐                    ┌─────────────┐
│   SAVING    │──────────────────▶  │  EXECUTING  │
└─────────────┘                    └──────┬──────┘
       │                                  │
       │ save failed                      │ execution completed
       ▼                                  ▼
┌─────────────┐                    ┌─────────────┐
│   ERROR    │◀───────────────────  │   RESULT    │
└─────────────┘                    └─────────────┘
       │                                  │
       │ dismiss                          │ done
       ▼                                  ▼
┌─────────────┐                    ┌─────────────┐
│    IDLE    │◀────────────────────│   EDITING   │
└─────────────┘                    └─────────────┘
```

---

## 10. 性能优化策略

### 10.1 工作流加载优化

| 策略 | 说明 |
|------|------|
| **懒加载 Actions** | 只加载可视区域内的 Action 节点 |
| **虚拟化列表** | Action 列表使用 LazyColumn |
| **缓存 Workflow** | Room 缓存 + 内存 LRU 缓存 |
| **增量更新** | 只更新变更的部分 UI |

### 10.2 执行性能优化

| 策略 | 说明 |
|------|------|
| **并行 Action** | 独立 Action 可并行执行 |
| **LLM 连接复用** | 复用 LiteRT-LM 连接 |
| **变量批量提交** | 减少数据库写入 |
| **流式输出** | LLM token 流式处理 |

### 10.3 内存优化

| 策略 | 说明 |
|------|------|
| **Image 引用** | 大图使用 URI 而非 Bitmap |
| **Action 池化** | 复用 Action Executor 实例 |
| **及时释放** | 执行完成后释放 LLM 资源 |
| **弱引用** | 非关键对象使用 WeakReference |

---

*文档版本: 1.0 | 最后更新: 2026-04-23*
