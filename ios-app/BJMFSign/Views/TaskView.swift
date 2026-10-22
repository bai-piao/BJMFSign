import MapKit
import SwiftUI

struct TaskView: View {
    @Bindable var viewModel: BjmfViewModel

    var body: some View {
        TaskListCard(viewModel: viewModel)
        TaskEditorCard(viewModel: viewModel)
    }
}

// MARK: - 任务列表

private struct TaskListCard: View {
    let viewModel: BjmfViewModel

    var body: some View {
        GlassCard {
            HStack {
                Text(tr("本机任务"))
                    .font(.title3.weight(.semibold))
                Spacer()
                Button {
                    viewModel.startNewTask()
                } label: {
                    Label(tr("新建"), systemImage: "plus")
                }
                .buttonStyle(.glass)
                .disabled(viewModel.isBusy)
            }

            if viewModel.tasks.isEmpty {
                Text(tr("还没有任务。扫码登录后会自动填入账号信息，选好位置和时间即可保存。"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(viewModel.tasks.enumerated()), id: \.element.id) { index, task in
                    TaskRow(
                        task: task,
                        selected: task.id == viewModel.selectedTaskId,
                        isBusy: viewModel.isBusy,
                        onSelect: { viewModel.selectTask(task) },
                        onToggle: { viewModel.toggleTask(task) },
                        onRunNow: { viewModel.runTaskNow(task) }
                    )
                    if index != viewModel.tasks.count - 1 {
                        RowDivider()
                    }
                }
            }
        }
    }
}

private struct TaskRow: View {
    let task: BjmfTask
    let selected: Bool
    let isBusy: Bool
    let onSelect: () -> Void
    let onToggle: () -> Void
    let onRunNow: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Circle()
                    .fill(task.enabled ? StatusStyle.enabledColor : Color.secondary.opacity(0.5))
                    .frame(width: 9, height: 9)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.name)
                        .font(.body.weight(.semibold))
                        .lineLimit(1)
                    Text(tr("班级 {0}", task.classId))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Toggle(tr("启用任务"), isOn: Binding(get: { task.enabled }, set: { _ in onToggle() }))
                    .labelsHidden()
                    .accessibilityValue(task.enabled ? tr("已启用") : tr("已停用"))
            }

            Label(
                NextRunText.label(times: task.times, dateStart: task.dateStart, dateEnd: task.dateEnd, enabled: task.enabled),
                systemImage: task.enabled ? "clock" : "pause.circle"
            )
            .font(.subheadline.weight(.medium))
            .foregroundStyle(task.enabled ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))

            if !task.times.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(task.times, id: \.self) { TimeChip(time: $0) }
                }
            }

            Text(locationText)
                .font(.footnote)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                Spacer()
                Button(tr("编辑"), action: onSelect)
                Button(tr("立即签到"), systemImage: "location.fill", action: onRunNow)
                    .disabled(isBusy)
            }
            .buttonStyle(.glass)
            .controlSize(.small)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, selected ? 12 : 0)
        .background {
            if selected {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(.tint.opacity(0.14))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
    }

    private var locationText: String {
        var text = tr("位置 {0}, {1} · 精度 {2}m", task.lng, task.lat, task.acc)
        if let range = NextRunText.dateRange(start: task.dateStart, end: task.dateEnd) {
            text += "\n" + tr("生效 {0}", range)
        }
        return text
    }
}

struct TimeChip: View {
    let time: String
    var onRemove: (() -> Void)?

    var body: some View {
        HStack(spacing: 4) {
            Text(NextRunText.shortTime(time))
                .font(.subheadline.weight(.medium).monospacedDigit())
            if onRemove != nil {
                Image(systemName: "xmark")
                    .font(.caption2.weight(.bold))
                    .opacity(0.7)
            }
        }
        .foregroundStyle(.tint)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.tint.opacity(0.14), in: .capsule)
        .contentShape(.capsule)
        .onTapGesture { onRemove?() }
        .accessibilityAddTraits(onRemove == nil ? [] : .isButton)
        .accessibilityHint(onRemove == nil ? "" : tr("点按移除该时间"))
    }
}

// MARK: - 任务编辑

private struct TaskEditorCard: View {
    @Bindable var viewModel: BjmfViewModel
    @State private var showTimePicker = false
    @State private var newTime = Calendar.current.date(bySettingHour: 7, minute: 30, second: 0, of: Date()) ?? Date()
    @State private var showCookie = false
    @State private var confirmDelete = false

    private var editing: Bool { viewModel.selectedTaskId != nil }
    private var times: [String] { BjmfViewModel.normalizeTimes(viewModel.taskForm.timesText) }

    var body: some View {
        GlassCard(title: editing ? tr("编辑任务") : tr("新建任务")) {
            SectionHeader(tr("基本信息"))
            HStack(spacing: 10) {
                LabeledField(label: tr("姓名"), text: $viewModel.taskForm.name)
                LabeledField(label: tr("班级 ID"), text: $viewModel.taskForm.classId, keyboard: .numberPad)
            }

            SectionSpacer()
            MapCoordinatePicker(viewModel: viewModel)

            VStack(alignment: .leading, spacing: 6) {
                Text(tr("定位精度（米）"))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    TextField(tr("精度"), text: $viewModel.taskForm.acc)
                        .keyboardType(.numberPad)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(.fill.tertiary, in: .rect(cornerRadius: 14))
                    ForEach(["10", "30", "50", "100"], id: \.self) { preset in
                        let isSelected = viewModel.taskForm.acc.trimmed == preset
                        Button(preset) { viewModel.taskForm.acc = preset }
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(isSelected ? AnyShapeStyle(.white) : AnyShapeStyle(.tint))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .background(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tint.opacity(0.14)), in: .capsule)
                            .buttonStyle(.plain)
                    }
                }
            }
            .padding(.top, 4)

            SectionSpacer()
            SectionHeader(tr("执行时间"))
            if times.isEmpty {
                Text(tr("尚未添加执行时间"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                FlowLayout(spacing: 6) {
                    ForEach(times, id: \.self) { time in
                        TimeChip(time: time) {
                            viewModel.taskForm.timesText = times.filter { $0 != time }.joined(separator: ",")
                        }
                    }
                }
            }
            HStack {
                Text(tr("点按时间可移除"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
                Button(tr("添加时间"), systemImage: "plus") { showTimePicker = true }
                    .buttonStyle(.glass)
                    .controlSize(.small)
            }

            SectionSpacer()
            SectionHeader(tr("生效日期"))
            OptionalDateRow(label: tr("开始日期"), text: $viewModel.taskForm.dateStart)
            OptionalDateRow(label: tr("结束日期"), text: $viewModel.taskForm.dateEnd)
            Text(tr("关闭表示不限制"))
                .font(.footnote)
                .foregroundStyle(.secondary)

            Label(
                NextRunText.label(times: times, dateStart: viewModel.taskForm.dateStart.nilIfEmpty, dateEnd: viewModel.taskForm.dateEnd.nilIfEmpty, enabled: true),
                systemImage: "clock.badge.checkmark"
            )
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(.tint.opacity(0.12), in: .rect(cornerRadius: 14))
            .padding(.top, 6)

            SectionSpacer()
            SectionHeader(tr("登录凭证"))
            PreferenceRow(
                title: "Cookie",
                summary: Self.cookieSummary(viewModel.taskForm.cookie),
                onTap: { withAnimation(.smooth) { showCookie.toggle() } }
            ) {
                Image(systemName: "chevron.down")
                    .rotationEffect(.degrees(showCookie ? 180 : 0))
                    .foregroundStyle(.secondary)
            }
            if showCookie || viewModel.taskForm.cookie.isBlankText {
                LabeledField(label: tr("Cookie（扫码登录后自动填入）"), text: $viewModel.taskForm.cookie, axis: .vertical)
            }

            SectionSpacer()
            SectionHeader(tr("通知推送（可选）"))
            LabeledField(label: tr("Server酱 Key（微信）"), text: $viewModel.taskForm.wxKey)
            LabeledField(label: tr("Qmsg酱 Key（QQ）"), text: $viewModel.taskForm.qqKey)

            GlassEffectContainer(spacing: 10) {
                VStack(spacing: 10) {
                    HStack(spacing: 10) {
                        Button {
                            viewModel.saveTask()
                        } label: {
                            Label(editing ? tr("保存修改") : tr("保存任务"), systemImage: "checkmark")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glassProminent)

                        Button(tr("用扫码账号")) {
                            viewModel.newTaskFromAccount()
                        }
                        .buttonStyle(.glass)
                    }
                    if editing {
                        HStack(spacing: 10) {
                            Button {
                                viewModel.runSelectedNow()
                            } label: {
                                Label(tr("立即签到"), systemImage: "location.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.glass)

                            Button(tr("删除任务"), role: .destructive) {
                                confirmDelete = true
                            }
                            .buttonStyle(.glass)
                        }
                    }
                }
                .controlSize(.large)
                .disabled(viewModel.isBusy)
            }
            .padding(.top, 10)
        }
        .animation(.smooth, value: times)
        .sheet(isPresented: $showTimePicker) {
            NavigationStack {
                DatePicker(tr("执行时间"), selection: $newTime, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .environment(\.locale, I18n.locale)
                    .navigationTitle(tr("添加执行时间"))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button(tr("取消"), role: .cancel) { showTimePicker = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button(tr("添加"), systemImage: "checkmark") { addTime() }
                        }
                    }
            }
            .presentationDetents([.height(320)])
        }
        .confirmationDialog(tr("删除任务「{0}」？", viewModel.taskForm.name), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(tr("删除任务"), role: .destructive) { viewModel.deleteSelectedTask() }
        } message: {
            Text(tr("该任务的定时提醒也会一并取消。"))
        }
    }

    private func addTime() {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: newTime)
        let text = String(format: "%02d:%02d:00", parts.hour ?? 0, parts.minute ?? 0)
        let merged = BjmfViewModel.normalizeTimes((times + [text]).joined(separator: ",")).sorted()
        viewModel.taskForm.timesText = merged.joined(separator: ",")
        showTimePicker = false
    }

    private static func cookieSummary(_ cookie: String) -> String {
        let value = cookie.trimmed
        if value.isEmpty { return tr("未填写") }
        let head = String(value.prefix(18))
        return value.count > 18 ? tr("{0}…（共 {1} 字符）", head, value.count) : head
    }
}

/// 可选日期：开关关闭时为空字符串（不限制），打开后用日期选择器编辑 yyyy-MM-dd。
private struct OptionalDateRow: View {
    let label: String
    @Binding var text: String

    var body: some View {
        HStack {
            Toggle(label, isOn: Binding(
                get: { !text.isEmpty },
                set: { on in text = on ? NextRunText.dateString(Date()) : "" }
            ))
            .fixedSize()
            Spacer()
            if !text.isEmpty {
                DatePicker(
                    label,
                    selection: Binding(
                        get: { NextRunText.date(from: text) ?? Date() },
                        set: { text = NextRunText.dateString($0) }
                    ),
                    displayedComponents: .date
                )
                .labelsHidden()
                .environment(\.locale, I18n.locale)
            } else {
                Text(tr("不限"))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

/// 地图选点 + 当前位置 + 位置收藏（对应 Android 端 MapCoordinatePicker）。
/// 中国大陆地区 Apple 地图底图为 GCJ-02 坐标，与高德坐标一致，可直接作为签到坐标使用。
private struct MapCoordinatePicker: View {
    @Bindable var viewModel: BjmfViewModel
    @State private var position: MapCameraPosition = .automatic

    private var selected: CLLocationCoordinate2D? {
        CoordinateUtils.parse(viewModel.taskForm.coord).map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(tr("签到位置"))
            Text(selectedText)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(2)

            MapReader { proxy in
                Map(position: $position) {
                    if let selected {
                        Marker(tr("签到点"), systemImage: "mappin", coordinate: selected)
                            .tint(.red)
                    }
                    UserAnnotation()
                }
                .mapStyle(.standard(pointsOfInterest: .excludingAll))
                .mapControls {
                    MapCompass()
                    MapScaleView()
                }
                .onTapGesture { point in
                    if let coordinate = proxy.convert(point, from: .local) {
                        viewModel.setCoordinate(lat: coordinate.latitude, lng: coordinate.longitude)
                    }
                }
            }
            .frame(height: 280)
            .clipShape(.rect(cornerRadius: 20))
            .accessibilityLabel(tr("地图选点，点按地图设置签到位置"))
            .overlay(alignment: .bottomTrailing) {
                Button {
                    viewModel.useCurrentLocationAndFavorite()
                } label: {
                    Image(systemName: "location.fill")
                        .font(.title3)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .glassEffect(.regular.interactive(), in: .circle)
                .padding(10)
                .accessibilityLabel(tr("定位并收藏"))
            }
            .onAppear { recenter(zoomIn: selected != nil) }
            .onChange(of: viewModel.taskForm.coord) { _, _ in recenter(zoomIn: true) }

            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.useCurrentLocationAndFavorite()
                    } label: {
                        Label(tr("定位并收藏"), systemImage: "location.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    Button {
                        viewModel.favoriteSelectedLocation()
                    } label: {
                        Label(tr("收藏选点"), systemImage: "star")
                    }
                    .buttonStyle(.glass)
                    .disabled(selected == nil)
                }
                .disabled(viewModel.isBusy)
            }

            if viewModel.favoriteLocations.isEmpty {
                PreferenceRow(title: tr("位置收藏"), summary: tr("可收藏当前位置或地图选点"))
            } else {
                Text(tr("位置收藏"))
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 4)
                ForEach(Array(viewModel.favoriteLocations.enumerated()), id: \.element.id) { index, location in
                    PreferenceRow(
                        title: location.name,
                        summary: "\(location.lng), \(location.lat)",
                        indicator: viewModel.accent.opacity(0.85),
                        onTap: { viewModel.useFavoriteLocation(location) }
                    ) {
                        Menu {
                            Button(tr("选用"), systemImage: "checkmark") { viewModel.useFavoriteLocation(location) }
                            Button(tr("删除"), systemImage: "trash", role: .destructive) { viewModel.deleteFavoriteLocation(location) }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.title3)
                        }
                        .accessibilityLabel(tr("更多操作"))
                    }
                    if index != viewModel.favoriteLocations.count - 1 {
                        RowDivider()
                    }
                }
            }
        }
    }

    private var selectedText: String {
        guard let selected else { return tr("点击地图选择签到点") }
        return tr("已选择：经度 {0}，纬度 {1}", CoordinateUtils.coordText(selected.longitude), CoordinateUtils.coordText(selected.latitude))
    }

    private func recenter(zoomIn: Bool) {
        let center = selected ?? CLLocationCoordinate2D(
            latitude: CoordinateUtils.defaultMapPoint.lat,
            longitude: CoordinateUtils.defaultMapPoint.lng
        )
        let span = zoomIn ? 0.006 : 0.15
        withAnimation(.smooth) {
            position = .region(MKCoordinateRegion(center: center, span: MKCoordinateSpan(latitudeDelta: span, longitudeDelta: span)))
        }
    }
}
