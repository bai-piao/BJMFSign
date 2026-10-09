import MapKit
import SwiftUI

struct TaskView: View {
    @Bindable var viewModel: BjmfViewModel

    var body: some View {
        if !viewModel.tasks.isEmpty {
            GlassCard(title: "本机任务") {
                ForEach(Array(viewModel.tasks.enumerated()), id: \.element.id) { index, task in
                    TaskRow(
                        task: task,
                        selected: task.id == viewModel.selectedTaskId,
                        onSelect: { viewModel.selectTask(task) },
                        onToggle: { viewModel.toggleTask(task) }
                    )
                    if index != viewModel.tasks.count - 1 {
                        RowDivider()
                    }
                }
            }
        }

        TaskFormCard(viewModel: viewModel)
    }
}

private struct TaskRow: View {
    let task: BjmfTask
    let selected: Bool
    let onSelect: () -> Void
    let onToggle: () -> Void

    var body: some View {
        let dateRange = [task.dateStart, task.dateEnd].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " - ")
        let summary = [
            task.times.isEmpty ? "未设置执行时间" : task.times.joined(separator: ", "),
            "\(task.lng), \(task.lat)  acc=\(task.acc)",
            dateRange,
        ].filter { !$0.isEmpty }.joined(separator: "\n")

        PreferenceRow(
            title: "\(task.name) / \(task.classId)",
            summary: summary,
            selected: selected,
            indicator: task.enabled ? StatusStyle.enabledColor : Color.secondary.opacity(0.5),
            onTap: onSelect
        ) {
            Toggle("启用", isOn: Binding(get: { task.enabled }, set: { _ in onToggle() }))
                .labelsHidden()
        }
        .contextMenu {
            Button("编辑", systemImage: "pencil", action: onSelect)
            Button(task.enabled ? "停用" : "启用", systemImage: task.enabled ? "pause.circle" : "play.circle", action: onToggle)
        }
    }
}

private struct TaskFormCard: View {
    @Bindable var viewModel: BjmfViewModel
    @State private var confirmDelete = false

    private var hasSelectedTask: Bool { viewModel.selectedTaskId != nil }

    var body: some View {
        GlassCard(title: hasSelectedTask ? "编辑签到任务" : "新建签到任务") {
            HStack(spacing: 10) {
                LabeledField(label: "姓名", text: $viewModel.taskForm.name)
                LabeledField(label: "班级 ID", text: $viewModel.taskForm.classId, keyboard: .numberPad)
            }

            MapCoordinatePicker(viewModel: viewModel)

            LabeledField(label: "定位精度", text: $viewModel.taskForm.acc, keyboard: .numberPad)
            LabeledField(label: "执行时间", text: $viewModel.taskForm.timesText, placeholder: "07:30:00,12:00:00")
            HStack(spacing: 10) {
                LabeledField(label: "开始日期", text: $viewModel.taskForm.dateStart, placeholder: "yyyy-MM-dd")
                LabeledField(label: "结束日期", text: $viewModel.taskForm.dateEnd, placeholder: "yyyy-MM-dd")
            }
            LabeledField(label: "Cookie", text: $viewModel.taskForm.cookie, axis: .vertical)
            LabeledField(label: "微信通知 Key", text: $viewModel.taskForm.wxKey)
            LabeledField(label: "Qmsg Key", text: $viewModel.taskForm.qqKey)

            GlassEffectContainer(spacing: 10) {
                VStack(spacing: 10) {
                    HStack(spacing: 10) {
                        Button {
                            viewModel.saveTask()
                        } label: {
                            Label("保存", systemImage: "checkmark")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glassProminent)

                        Button("用扫码账号") {
                            viewModel.newTaskFromAccount()
                        }
                        .buttonStyle(.glass)
                    }
                    if hasSelectedTask {
                        HStack(spacing: 10) {
                            Button {
                                viewModel.runSelectedNow()
                            } label: {
                                Label("立即签到", systemImage: "location.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.glass)

                            Button("删除任务", role: .destructive) {
                                confirmDelete = true
                            }
                            .buttonStyle(.glass)
                        }
                    }
                }
                .controlSize(.large)
                .disabled(viewModel.isBusy)
            }
            .padding(.top, 6)
        }
        .confirmationDialog("删除该任务？", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("删除任务", role: .destructive) { viewModel.deleteSelectedTask() }
        }
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
            Text("签到位置")
                .font(.body.weight(.semibold))
            Text(selectedText)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(2)

            MapReader { proxy in
                Map(position: $position) {
                    if let selected {
                        Marker("签到点", systemImage: "mappin", coordinate: selected)
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
                .accessibilityLabel("定位并收藏")
            }
            .onAppear { recenter(zoomIn: selected != nil) }
            .onChange(of: viewModel.taskForm.coord) { _, _ in recenter(zoomIn: true) }

            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.useCurrentLocationAndFavorite()
                    } label: {
                        Label("定位并收藏", systemImage: "location.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    Button {
                        viewModel.favoriteSelectedLocation()
                    } label: {
                        Label("收藏选点", systemImage: "star")
                    }
                    .buttonStyle(.glass)
                    .disabled(selected == nil)
                }
                .disabled(viewModel.isBusy)
            }

            if viewModel.favoriteLocations.isEmpty {
                PreferenceRow(title: "位置收藏", summary: "可收藏当前位置或地图选点")
            } else {
                Text("位置收藏")
                    .font(.body.weight(.semibold))
                    .padding(.top, 4)
                ForEach(Array(viewModel.favoriteLocations.enumerated()), id: \.element.id) { index, location in
                    PreferenceRow(
                        title: location.name,
                        summary: "\(location.lng), \(location.lat)",
                        indicator: viewModel.accent.opacity(0.85),
                        onTap: { viewModel.useFavoriteLocation(location) }
                    ) {
                        Menu {
                            Button("选用", systemImage: "checkmark") { viewModel.useFavoriteLocation(location) }
                            Button("删除", systemImage: "trash", role: .destructive) { viewModel.deleteFavoriteLocation(location) }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.title3)
                        }
                    }
                    if index != viewModel.favoriteLocations.count - 1 {
                        RowDivider()
                    }
                }
            }
        }
    }

    private var selectedText: String {
        guard let selected else { return "点击地图选择签到点" }
        return "已选择：经度 \(CoordinateUtils.coordText(selected.longitude))，纬度 \(CoordinateUtils.coordText(selected.latitude))"
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
