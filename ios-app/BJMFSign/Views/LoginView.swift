import SwiftUI

struct LoginView: View {
    let viewModel: BjmfViewModel

    var body: some View {
        GlassCard(title: "微信扫码添加账号") {
            Text(viewModel.qrStatusText)
                .foregroundStyle(.secondary)

            if let data = viewModel.qrImageData {
                Group {
                    if let image = UIImage(data: data) {
                        Image(uiImage: image)
                            .resizable()
                            .interpolation(.none)
                            .scaledToFit()
                            .padding(18)
                            .background(.white, in: .rect(cornerRadius: 20))
                            .accessibilityLabel("微信登录二维码")
                    } else {
                        Text("二维码解析失败")
                            .frame(maxWidth: .infinity, minHeight: 120)
                    }
                }
                .frame(maxWidth: 360)
                .frame(maxWidth: .infinity)
                .transition(.scale.combined(with: .opacity))

                Text("可截图后在微信中长按识别，或用另一台设备的微信扫描")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.createQrSession()
                    } label: {
                        Label(viewModel.qrImageData == nil ? "获取二维码" : "重新获取", systemImage: "qrcode")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(viewModel.isBusy || viewModel.isPolling)

                    Button("清除", role: .destructive) {
                        viewModel.clearQrSession()
                    }
                    .buttonStyle(.glass)
                    .disabled(viewModel.qrImageData == nil)
                }
                .controlSize(.large)
            }
        }
        .animation(.smooth, value: viewModel.qrImageData)

        if let user = viewModel.account?.userInfo {
            GlassCard(title: "当前扫码账号") {
                PreferenceRow(title: "姓名", value: user.name)
                RowDivider()
                PreferenceRow(title: "班级", value: user.className)
                RowDivider()
                PreferenceRow(title: "班级 ID", value: user.classId)
                RowDivider()
                PreferenceRow(title: "班级码", value: user.classCode)
            }
        }
    }
}
