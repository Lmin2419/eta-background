package io.github.mangi.eta.agent.context

import java.math.BigInteger
import java.time.Instant

internal enum class PersonalContextFieldKind { TEXT, INTEGER, DECIMAL }

internal data class PersonalContextTimeBounds(val startInclusive: Long?, val endExclusive: Long?)

internal enum class PersonalContextTimeUnit(val unitsPerSecond: Long, val requiresNumericCast: Boolean = false) {
    MILLISECONDS(1_000),
    SECONDS(1),
    TEXT_MILLISECONDS(1_000, requiresNumericCast = true);

    /** 整数时间戳的左右边界都取 ceil，才能精确保留原始左闭右开区间。 */
    fun toBounds(start: Instant?, end: Instant?): PersonalContextTimeBounds = try {
        PersonalContextTimeBounds(start?.let(::ceiling), end?.let(::ceiling))
    } catch (_: ArithmeticException) {
        throw PersonalContextArgumentException("时间范围超出数据源支持的整数范围")
    }

    private fun ceiling(value: Instant): Long {
        val nanosPerUnit = 1_000_000_000L / unitsPerSecond
        val fractional = (value.nano.toLong() + nanosPerUnit - 1) / nanosPerUnit
        return BigInteger.valueOf(value.epochSecond).multiply(BigInteger.valueOf(unitsPerSecond))
            .add(BigInteger.valueOf(fractional)).longValueExact()
    }
}

internal data class PersonalContextField(
    val logicalName: String,
    val physicalCandidates: List<String>,
    val kind: PersonalContextFieldKind = PersonalContextFieldKind.TEXT,
    val required: Boolean = false,
    val exposed: Boolean = true,
    val timeUnit: PersonalContextTimeUnit? = null,
    val description: String? = null,
)

internal enum class PersonalContextFilterOperator { EQUALS, NULL_OR_EQUALS }

internal data class PersonalContextFixedFilter(
    val fieldName: String,
    val operator: PersonalContextFilterOperator,
    val value: Long,
)

internal data class PersonalContextSource(
    val id: String,
    val label: String,
    val resource: String,
    val fields: List<PersonalContextField>,
    val searchFields: List<String>,
    val timeField: String?,
    val timeUnit: PersonalContextTimeUnit?,
    val timeDescription: String?,
    val fixedFilters: List<PersonalContextFixedFilter>,
) {
    init {
        require(fields.map { it.logicalName }.distinct().size == fields.size)
        require(searchFields.all { name -> fields.any { it.logicalName == name && it.exposed } })
        require((timeField == null) == (timeUnit == null))
        require(timeField == null || field(timeField)?.timeUnit == timeUnit)
        require(fixedFilters.all { field(it.fieldName)?.required == true })
    }

    fun field(logicalName: String): PersonalContextField? = fields.firstOrNull { it.logicalName == logicalName }

    /** 所有 action 都必须先确认这些列，不能因保护列缺失而移除过滤条件。 */
    val probeRequired: List<PersonalContextField> get() = fields.filter { it.required }
    val exposedFields: List<PersonalContextField> get() = fields.filter { it.exposed }
    val sortField: String? get() = timeField ?: field("indexed_time")?.logicalName
    val sortTimeKind: String get() = if (timeField == null) "indexed" else "business"
}

internal object PersonalContextSources {
    const val BILL_CURRENCY = "CNY"
    const val BILL_AMOUNT_UNIT = "yuan"
    const val BILL_EXPENSES = "expenses"
    const val BILL_INCOME = "income"

    private val valid = PersonalContextField("_valid", listOf("valid"), PersonalContextFieldKind.INTEGER, required = true, exposed = false)
    private val stableId = PersonalContextField("id", listOf("docID"), PersonalContextFieldKind.INTEGER, required = true)
    private val validFilter = PersonalContextFixedFilter("_valid", PersonalContextFilterOperator.EQUALS, 1)

    val all: List<PersonalContextSource> = listOf(
        source(
            "photos", "照片与截图", "gallery",
            listOf(
                integer("origin_id", "media_idCom", required = true), text("uri", "uriCom"),
                text("title", "titleCom"), text("text", "ocrCom"),
                text("caption", "caption_content0Com", "caption_contentCom"), text("tags", "tagsCom"),
                text("album", "bucket_nameCom"), text("location", "locationCom"),
                protection("_media_type", "typeCom"), time("time", "date_modifiedCom", PersonalContextTimeUnit.SECONDS),
                time("taken_time", "datetakenCom"),
            ), listOf("text", "caption", "tags", "location", "album"), "time", PersonalContextTimeUnit.SECONDS,
            "系统索引中的图片修改时间，单位为秒；拍摄时间另见 taken_time，不代表当前媒体库状态", listOf(equal("_media_type", 1)),
        ),
        source(
            "notes", "便签", "com.coloros.note#note",
            listOf(
                text("origin_id", "local_idCom"), text("title", "titleCom"), text("text", "textCom"),
                text("folder", "folder_nameCom"), time("created_time", "create_timeCom"), time("time", "update_timeCom"),
                protection("_recycled", "recycle_timeCom"), protection("_encrypted", "encryptedCom"),
            ), listOf("title", "text", "folder"), "time", PersonalContextTimeUnit.MILLISECONDS, "便签最后编辑时间",
            listOf(equal("_recycled", 0), equal("_encrypted", 0)),
        ),
        source(
            "recordings", "录音", "recorder",
            listOf(
                integer("origin_id", "idCom"), text("title", "display_nameCom"), text("audio_path", "media_pathCom"),
                text("transcript_path", "text_pathCom"), integer("duration_ms", "durationCom"), text("folder", "bucketCom"),
                integer("has_transcript", "supportContentCom"), integer("has_summary", "summary_text_flagCom"),
                time("time", "date_modifiedCom"),
            ), listOf("title", "folder"), "time", PersonalContextTimeUnit.MILLISECONDS, "录音最后修改时间；转写路径不等于转写正文",
        ),
        source(
            "calendar", "日程", "calendar",
            listOf(
                text("origin_id", "idCom"), text("title", "nameCom"), text("text", "descriptionCom"), text("location", "locationCom"),
                time("time", "startTimeCom"), time("end_time", "endTimeCom"), text("timezone", "timezoneCom"),
                text("recurrence", "isRepeatCom"), integer("all_day", "allDayIndexCom"),
            ), listOf("title", "text", "location"), "time", PersonalContextTimeUnit.MILLISECONDS,
            "日程开始时间；重复实例是否展开取决于系统索引，返回 instance_id 时为具体实例；按 id 读取原始日程，不保证覆盖所有重复事件",
        ),
        source(
            "calendar_todos", "日历待办", "calendarTodo",
            listOf(
                text("origin_id", "idCom"), text("text", "contentCom"), text("source_package", "create_packageCom"),
                time("time", "start_timeCom", PersonalContextTimeUnit.TEXT_MILLISECONDS),
                time("planned_end_time", "finish_timeCom", PersonalContextTimeUnit.TEXT_MILLISECONDS),
                text("recurrence", "rruleCom"), text("reminders", "remindTimesCom"), integer("starred", "starCom"),
                time("source_created_time", "create_timeCom", PersonalContextTimeUnit.TEXT_MILLISECONDS, "来源提供的创建时间，不用于计划开始时间过滤"),
            ), listOf("text", "source_package"), "time", PersonalContextTimeUnit.TEXT_MILLISECONDS,
            "待办计划开始时间；系统可能展开重复实例但不提供实例 ID，按 id 读取原始待办，不保证覆盖所有重复事件；计划结束时间不表示已完成",
        ),
        source(
            "todos", "待办记录", "com.oplus.deepthinker#donate_todo_record",
            listOf(
                text("origin_id", "idCom"), text("title", "todoTitleCom"), text("text", "todoDescriptionCom"),
                text("context", "descriptionCom"), text("original_text", "originalTodoCom"), text("status", "statusCom"),
                text("origin", "sourceCom"), time("time", "eventStartTimeMillisCom", description = "0 表示未识别明确开始时间"),
                time("deadline", "todoDeadlineTimeCom", description = "小于或等于 0 表示没有明确截止时间"),
                time("indexed_time", "creationTimestampMillisCom", description = "记录生成或抽取时间，不是活动发生时间"),
            ), listOf("title", "text", "context", "original_text"), "time", PersonalContextTimeUnit.MILLISECONDS,
            "识别出的待办开始时间；0 表示未知，按时间过滤不会补搜只写在文本中的日期",
        ),
        source(
            "memories", "系统记忆", "com.oplus.aimemory#donate_AIMemory",
            listOf(
                text("origin_id", "memoryIdCom"), text("title", "titleCom"), text("text", "dataTextCom"),
                text("summary", "summaryCom", "titleSummaryCom"), text("description", "descriptionCom"),
                text("notes", "notesCom"), text("memory_type", "memoryTypeCom"), text("source_app", "appNameCom"),
                time("time", "createdTimeCom"), time("updated_time", "updateTimeCom"),
                time("indexed_time", "creationTimestampMillisCom", description = "系统索引写入时间，不是记忆创建时间"),
                protection("_recycled", "recycleTimeCom"),
            ), listOf("title", "text", "summary", "description", "notes", "source_app"), "time", PersonalContextTimeUnit.MILLISECONDS,
            "记忆创建时间", listOf(nullOrEqual("_recycled", 0)),
        ),
        source(
            "bills", "账单", "com.oplus.aimemory#billsDonate_Bills",
            listOf(
                text("origin_id", "billIdCom"), text("title", "productNameCom"), text("merchant", "merchantNameCom"),
                PersonalContextField("amount", listOf("amountCom"), PersonalContextFieldKind.DECIMAL, description = "金额单位为人民币元"),
                text("category", "categoryCom"), text("transaction_type", "transactionTypeCom"), text("transaction_status", "transactionStatusCom"),
                time("transaction_time", "transactionTimeCom"), text("payment_source", "paymentSourceCom"),
                protection("_recycled", "recycleTimeCom"),
            ), listOf("title", "merchant", "category", "transaction_status", "payment_source"), "transaction_time", PersonalContextTimeUnit.MILLISECONDS,
            "账单记录的交易时间；交易状态仅按原文展示，不推断退款净额", listOf(nullOrEqual("_recycled", 0)),
        ),
        source(
            "collections", "记忆合集", "com.oplus.aimemory#collectionDonate_Collection",
            listOf(
                text("origin_id", "collectionIdCom"), text("title", "collectionNameCom"), text("text", "collectionSummaryCom"),
                text("summary_title", "collectionSummaryTitleCom"), text("member_ids", "memoryIdsCom"),
                time("indexed_time", "creationTimestampMillisCom", description = "仅为索引写入时间，不能表示合集首次创建时间"),
            ), listOf("title", "text", "summary_title"), null, null,
            "没有已确认的业务时间字段；不支持按业务时间过滤",
        ),
        source(
            "events", "生活事件", "com.oplus.deepthinker#donate_daily_event",
            listOf(text("title", "eventNameCom"), text("text", "eventDescriptionCom"), time("time", "startTimeMillisCom"),
                time("end_time", "endTimeMillisCom", description = "空值或 0 可以表示进行中")),
            listOf("title", "text"), "time", PersonalContextTimeUnit.MILLISECONDS, "事件开始时间；推断事件不是已确认事实",
        ),
        source(
            "notifications", "历史通知", "com.oplus.deepthinker#donate_notification",
            listOf(
                text("origin_id", "notificationKeyCom"), text("title", "titleCom"), text("text", "descriptionCom"),
                text("subtext", "subTextCom"), text("source_package", "packageNameCom"), text("channel", "channelIdCom"),
                time("time", "eventTimeCom"), time("indexed_time", "createTimeCom", description = "系统索引创建时间，不是通知事件时间"),
            ), listOf("title", "text", "subtext", "source_package"), "time", PersonalContextTimeUnit.MILLISECONDS,
            "历史通知事件时间；不代表当前通知栏或当前未读状态",
        ),
        source(
            "files", "本地文件", "file",
            listOf(
                text("origin_id", "idCom"), text("name", "filenameCom"), text("title", "titleCom"), text("text", "highlight"),
                text("path", "absolutePathCom"), integer("size_bytes", "sizeCom"), integer("file_type", "typeCom"), time("time", "lastModifiedCom"),
            ), listOf("name", "title", "text"), "time", PersonalContextTimeUnit.MILLISECONDS,
            "索引中记录的文件最后修改时间；路径不证明文件当前仍存在",
        ),
        source(
            "app_files", "应用文件", "com.coloros.filemanager#third_app_file",
            listOf(text("name", "nameCom"), text("source_package", "packageCom"), text("source_app", "source_nameCom"),
                integer("size_bytes", "sizeCom"), time("time", "file_timeCom")),
            listOf("name", "source_package", "source_app"), "time", PersonalContextTimeUnit.MILLISECONDS, "来源文件的文件系统时间",
        ),
        source(
            "flights", "航班行程", "standard_data#flight",
            listOf(
                text("origin_id", "idCom"), text("title", "titleCom"), text("text", "descriptionCom"), text("number", "flightNumberCom"),
                text("carrier", "airlineNameCom"), text("departure", "departureAirportNameCom"), text("arrival", "arrivalAirportNameCom"),
                text("status", "flightStatusCom"), time("time", "planDepartureTimestampCom"), time("arrival_time", "planArrivalTimestampCom"),
            ), listOf("title", "text", "number", "carrier", "departure", "arrival"), "time", PersonalContextTimeUnit.MILLISECONDS, "计划起飞时间",
        ),
        source(
            "hotels", "酒店预订", "standard_data#hotel",
            listOf(
                text("origin_id", "idCom"), text("title", "hotelNameCom"), text("text", "descriptionCom"), text("address", "addressCom"),
                text("order_number", "orderNumberCom"), text("room_type", "room_typeCom"), text("status", "statusCom"),
                time("time", "checkInTimestampCom"), time("checkout_time", "checkoutTimestampCom"),
            ), listOf("title", "text", "address", "order_number"), "time", PersonalContextTimeUnit.MILLISECONDS, "预订入住时间",
        ),
        source(
            "trains", "火车行程", "standard_data#train",
            listOf(
                text("origin_id", "idCom"), text("title", "titleCom"), text("text", "descriptionCom"), text("number", "trainNumberCom"),
                text("departure", "departureStationNameCom"), text("arrival", "arrivalStationNameCom"), integer("status", "trainStatusCom"),
                time("time", "planDepartureTimestampCom"), time("arrival_time", "planArrivalTimestampCom"),
            ), listOf("title", "text", "number", "departure", "arrival"), "time", PersonalContextTimeUnit.MILLISECONDS, "计划出发时间",
        ),
    )

    private val byId = all.associateBy { it.id }
    val ids: Set<String> get() = byId.keys
    fun find(id: String): PersonalContextSource? = byId[id]

    private fun source(
        id: String,
        label: String,
        resource: String,
        fields: List<PersonalContextField>,
        searchFields: List<String>,
        timeField: String?,
        timeUnit: PersonalContextTimeUnit?,
        timeDescription: String?,
        extraFilters: List<PersonalContextFixedFilter> = emptyList(),
    ) = PersonalContextSource(id, label, resource, listOf(stableId, valid) + fields, searchFields, timeField, timeUnit, timeDescription,
        listOf(validFilter) + extraFilters)

    private fun text(name: String, vararg physical: String) = PersonalContextField(name, physical.toList())
    private fun integer(name: String, physical: String, required: Boolean = false) =
        PersonalContextField(name, listOf(physical), PersonalContextFieldKind.INTEGER, required = required)
    private fun time(name: String, physical: String, unit: PersonalContextTimeUnit = PersonalContextTimeUnit.MILLISECONDS, description: String? = null) =
        PersonalContextField(name, listOf(physical), PersonalContextFieldKind.INTEGER, timeUnit = unit, description = description)
    private fun protection(name: String, physical: String) =
        PersonalContextField(name, listOf(physical), PersonalContextFieldKind.INTEGER, required = true, exposed = false)
    private fun equal(name: String, value: Long) = PersonalContextFixedFilter(name, PersonalContextFilterOperator.EQUALS, value)
    private fun nullOrEqual(name: String, value: Long) = PersonalContextFixedFilter(name, PersonalContextFilterOperator.NULL_OR_EQUALS, value)
}
