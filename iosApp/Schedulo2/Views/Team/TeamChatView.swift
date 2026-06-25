import SwiftUI
import PhotosUI

struct TeamChatView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var messageText = ""
    @State private var isAnnouncement = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var showHelp = false

    private var isOwner: Bool {
        teamViewModel.currentTeam?.ownerId == teamViewModel.currentUserId
    }

    private var pinnedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.filter { $0.isPinned }
    }

    private var sortedMessages: [TeamMessageInfo] {
        teamViewModel.teamMessages.sorted { $0.createdAt > $1.createdAt }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !pinnedMessages.isEmpty {
                    pinnedSection
                }
                messageList
                if teamViewModel.isUploadingImage {
                    ProgressView()
                        .tint(.primaryGreen)
                        .padding(.vertical, 4)
                }
                if teamViewModel.isManager {
                    announceToggle
                }
                Divider()
                inputBar
            }
            .navigationTitle("Team Chat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showHelp = true }) {
                        Image(systemName: "questionmark.circle")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onChange(of: selectedPhoto) { newItem in
                guard let item = newItem else { return }
                item.loadTransferable(type: Data.self) { result in
                    if case .success(let data) = result, let data = data, let image = UIImage(data: data) {
                        DispatchQueue.main.async {
                            teamViewModel.sendImage(image)
                        }
                    } else {
                        DispatchQueue.main.async {
                            teamViewModel.errorMessage = "Couldn't read that photo. Please try another."
                        }
                    }
                }
                selectedPhoto = nil
            }
            .alert("Team Chat", isPresented: $showHelp) {
                Button("Got it", role: .cancel) {}
            } message: {
                Text("Message your whole team in real time. Managers can post announcements and pin messages. Share photos with the attach button — images auto-expire once everyone has seen them.")
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { teamViewModel.errorMessage != nil },
                set: { if !$0 { teamViewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { teamViewModel.errorMessage = nil }
            } message: {
                Text(teamViewModel.errorMessage ?? "")
            }
        }
    }

    private var pinnedSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Image(systemName: "pin.fill")
                    .font(.system(size: 11))
                    .foregroundColor(.accentOrange)
                Text("Pinned")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.accentOrange)
            }
            ForEach(pinnedMessages.prefix(3)) { msg in
                let preview: String = msg.imageUrl.isEmpty ? msg.text : "Sent a photo"
                Text("\(msg.senderName.isEmpty ? "Unknown" : msg.senderName): \(preview)")
                    .font(.system(size: 12))
                    .lineLimit(1)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.accentOrange.opacity(0.1))
        )
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }

    // Oldest → newest, top to bottom (sortedMessages is newest-first).
    private var chronologicalMessages: [TeamMessageInfo] {
        Array(sortedMessages.reversed())
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(Array(chronologicalMessages.enumerated()), id: \.element.id) { index, message in
                        let prev = index > 0 ? chronologicalMessages[index - 1] : nil
                        let isGroupStart = prev == nil || prev?.senderId != message.senderId
                        ChatBubbleView(
                            message: message,
                            isMe: message.senderId == teamViewModel.currentUserId,
                            isOwner: isOwner,
                            isGroupStart: isGroupStart,
                            memberCount: teamViewModel.members.count
                        )
                        .id(message.id)
                        .padding(.top, isGroupStart ? 6 : 1)
                        .onAppear {
                            if !message.imageUrl.isEmpty {
                                teamViewModel.markMessageSeen(messageId: message.id)
                            }
                        }
                    }
                    Color.clear.frame(height: 1).id("chat_bottom_anchor")
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
            }
            .background(Color(UIColor.secondarySystemBackground).opacity(0.4))
            .frame(maxHeight: .infinity)
            // Keep the newest message in view, WhatsApp/Telegram style.
            .onChange(of: chronologicalMessages.count) { _ in
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo("chat_bottom_anchor", anchor: .bottom)
                }
            }
            .onAppear {
                proxy.scrollTo("chat_bottom_anchor", anchor: .bottom)
            }
        }
    }

    private var announceToggle: some View {
        HStack {
            Button(action: { isAnnouncement.toggle() }) {
                HStack(spacing: 4) {
                    Image(systemName: isAnnouncement ? "megaphone.fill" : "megaphone")
                        .font(.system(size: 12))
                    Text("Announce")
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundColor(isAnnouncement ? .white : .accentOrange)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(isAnnouncement ? Color.accentOrange : Color.accentOrange.opacity(0.12))
                )
            }
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }

    private var canSend: Bool {
        !messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            HStack(spacing: 4) {
                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    Image(systemName: "photo.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.secondary)
                }
                .disabled(teamViewModel.isUploadingImage)

                TextField("Message", text: $messageText, axis: .vertical)
                    .font(.system(size: 16))
                    .lineLimit(1...4)
                    .padding(.vertical, 6)
            }
            .padding(.horizontal, 12)
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(Color(UIColor.secondarySystemBackground))
            )

            Button(action: {
                guard canSend else { return }
                teamViewModel.sendMessage(text: messageText, isAnnouncement: isAnnouncement)
                messageText = ""
                isAnnouncement = false
            }) {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(canSend ? Color.primaryGreen : Color.gray.opacity(0.4)))
            }
            .disabled(!canSend)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
    }
}

private extension DateFormatter {
    static let chatTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()
}

private struct ChatBubbleView: View {
    let message: TeamMessageInfo
    let isMe: Bool
    let isOwner: Bool
    let isGroupStart: Bool
    let memberCount: Int
    @EnvironmentObject var teamViewModel: TeamViewModel
    @State private var showActions = false

    private var bgColor: Color {
        if message.isAnnouncement { return .accentOrange.opacity(0.15) }
        if isMe { return .primaryGreen.opacity(0.22) }
        return Color(UIColor.systemBackground)
    }

    // Asymmetric "tail" corner like WhatsApp/Telegram.
    private var bubbleShape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: 16,
            bottomLeadingRadius: isMe ? 16 : 4,
            bottomTrailingRadius: isMe ? 4 : 16,
            topTrailingRadius: 16
        )
    }

    var body: some View {
        VStack(alignment: isMe ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMe { Spacer(minLength: 50) }
                VStack(alignment: .leading, spacing: 3) {
                    if message.isAnnouncement {
                        announcementBadge
                    }
                    if !isMe && isGroupStart {
                        let senderName: String = message.senderName.isEmpty ? "Unknown" : message.senderName
                        Text(senderName)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.primaryGreen)
                    }
                    if !message.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: message.imageUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(maxWidth: 220, maxHeight: 220)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            case .failure:
                                Label("Image expired", systemImage: "photo.badge.exclamationmark")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            default:
                                ProgressView()
                                    .frame(width: 120, height: 120)
                            }
                        }
                    }
                    if !message.text.isEmpty {
                        Text(message.text)
                            .font(.system(size: 16))
                    }
                    timestampRow
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(bubbleShape.fill(bgColor))
                .overlay(bubbleShape.stroke(Color.primary.opacity(0.06), lineWidth: 0.5))
                .onTapGesture { if isMe || isOwner { showActions.toggle() } }
                if !isMe { Spacer(minLength: 50) }
            }

            if showActions && (isMe || isOwner) {
                actionButtons
            }
        }
        .frame(maxWidth: .infinity, alignment: isMe ? .trailing : .leading)
    }

    private var announcementBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "megaphone.fill")
                .font(.system(size: 11))
                .foregroundColor(.accentOrange)
            Text("Announcement")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.accentOrange)
        }
    }

    private var timestampRow: some View {
        HStack(spacing: 3) {
            Spacer(minLength: 0)
            if message.isPinned {
                Image(systemName: "pin.fill")
                    .font(.system(size: 9))
                    .foregroundColor(.accentOrange)
            }
            Text(DateFormatter.chatTime.string(from: message.createdDate))
                .font(.system(size: 10))
                .foregroundColor(.secondary)
            if !message.imageUrl.isEmpty && !message.seenBy.isEmpty {
                let allSeen = memberCount > 0 && message.seenBy.count >= memberCount
                Image(systemName: allSeen ? "checkmark.circle.fill" : "checkmark")
                    .font(.system(size: 10))
                    .foregroundColor(allSeen ? .accentBlue : .secondary)
                Text("\(message.seenBy.count)")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 16) {
            if isOwner {
                Button(action: { teamViewModel.togglePin(messageId: message.id); showActions = false }) {
                    Image(systemName: message.isPinned ? "pin.slash" : "pin")
                        .font(.system(size: 13))
                        .foregroundColor(message.isPinned ? .accentOrange : .secondary)
                }
            }
            Button(action: { teamViewModel.deleteMessage(messageId: message.id); showActions = false }) {
                Image(systemName: "trash")
                    .font(.system(size: 13))
                    .foregroundColor(.red.opacity(0.7))
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
    }
}
