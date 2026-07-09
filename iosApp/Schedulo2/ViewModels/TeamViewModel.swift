import Foundation
import Combine
import UIKit
import UserNotifications
import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage

struct TeamInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String = ""
    var ownerId: String = ""
    var inviteCode: String = ""
    var createdAt: Int64 = 0
    var memberCount: Int = 0
    var weeklyCycleStartDay: String = "Monday"
    // Company the team works for (used as the company on team shifts).
    var companyName: String = ""
    // Working hours. When open24Hours is true the start/end are ignored.
    var open24Hours: Bool = true
    var workStartMinutes: Int = 0   // minutes from midnight
    var workEndMinutes: Int = 0
    // Structured location/address.
    var addressLine: String = ""
    var city: String = ""
    var region: String = ""         // state / province / region
    var postalCode: String = ""
}

struct TeamMemberInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var userId: String = ""
    var role: String = "member"
    var joinedAt: Int64 = 0
    var displayName: String = ""
    var email: String = ""
    // Manager-set default pay rate, prefilled when assigning shifts.
    var defaultHourlyRate: Double = 0.0
}

struct ShiftTaskInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var isCompleted: Bool = false
}

struct TeamShiftInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var assignedTo: String = ""
    var assignedBy: String = ""
    var company: String = ""
    var role: String = ""
    var startTime: Int64 = 0
    var endTime: Int64 = 0
    var hourlyRate: Double = 0.0
    var notes: String = ""
    var status: String = "accepted"
    var tasks: [ShiftTaskInfo] = []

    var durationHours: Double {
        guard endTime > startTime else { return 0.0 }
        return Double(endTime - startTime) / 3_600_000.0
    }

    var startDate: Date {
        Date(timeIntervalSince1970: Double(startTime) / 1000.0)
    }

    var endDate: Date {
        Date(timeIntervalSince1970: Double(endTime) / 1000.0)
    }
}

struct TeamMessageInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var senderId: String = ""
    var senderName: String = ""
    var text: String = ""
    var isAnnouncement: Bool = false
    var isPinned: Bool = false
    var imageUrl: String = ""
    var seenBy: [String] = []
    var createdAt: Int64 = 0

    var createdDate: Date {
        Date(timeIntervalSince1970: Double(createdAt) / 1000.0)
    }
}

struct SwapRequestInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var requesterId: String = ""
    var requesterName: String = ""
    var requesterShiftId: String = ""
    var targetMemberId: String = ""
    var targetMemberName: String = ""
    var targetShiftId: String = ""
    var status: String = "pending"
    var createdAt: Int64 = 0
    var resolvedAt: Int64 = 0
    var resolvedBy: String = ""
}

struct MemberJobInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var defaultHourlyRate: Double = 15.0
    var isGigWork: Bool = false
}

struct TaskHistoryEntryInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var status: String = ""
    var changedBy: String = ""
    var changedByName: String = ""
    var timestamp: Int64 = 0

    var date: Date { Date(timeIntervalSince1970: Double(timestamp) / 1000.0) }
}

struct TeamTaskInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var title: String = ""
    var taskDescription: String = ""
    var assignedTo: String = ""
    var assignedToName: String = ""
    var assignedBy: String = ""
    var status: String = "pending"   // pending | in_progress | completed
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0
    var history: [TaskHistoryEntryInfo] = []
}

/// All editable team fields captured by the Create / Edit team forms.
struct TeamFormData {
    var name: String = ""
    var companyName: String = ""
    var weeklyCycleStartDay: String = "Monday"
    var open24Hours: Bool = true
    var workStartMinutes: Int = 9 * 60
    var workEndMinutes: Int = 17 * 60
    var addressLine: String = ""
    var city: String = ""
    var region: String = ""
    var postalCode: String = ""

    init() {}

    init(from team: TeamInfo) {
        name = team.name
        companyName = team.companyName
        weeklyCycleStartDay = team.weeklyCycleStartDay
        open24Hours = team.open24Hours
        workStartMinutes = team.open24Hours ? 9 * 60 : team.workStartMinutes
        workEndMinutes = team.open24Hours ? 17 * 60 : team.workEndMinutes
        addressLine = team.addressLine
        city = team.city
        region = team.region
        postalCode = team.postalCode
    }

    /// Non-identity team fields written to Firestore on create and update.
    func firestoreFields() -> [String: Any] {
        [
            "weeklyCycleStartDay": weeklyCycleStartDay,
            "companyName": companyName.trimmingCharacters(in: .whitespaces),
            "open24Hours": open24Hours,
            "workStartMinutes": workStartMinutes,
            "workEndMinutes": workEndMinutes,
            "addressLine": addressLine.trimmingCharacters(in: .whitespaces),
            "city": city.trimmingCharacters(in: .whitespaces),
            "region": region.trimmingCharacters(in: .whitespaces),
            "postalCode": postalCode.trimmingCharacters(in: .whitespaces)
        ]
    }
}

/// Human-readable working-hours summary, e.g. "Open 24 hours" or "9:00 AM – 5:00 PM".
func formatWorkHours(open24Hours: Bool, startMinutes: Int, endMinutes: Int) -> String {
    if open24Hours { return "Open 24 hours" }
    func fmt(_ mins: Int) -> String {
        let h = (mins / 60) % 24
        let m = mins % 60
        let date = Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: Date()) ?? Date()
        let f = DateFormatter()
        f.dateFormat = "h:mm a"
        return f.string(from: date)
    }
    return "\(fmt(startMinutes)) – \(fmt(endMinutes))"
}

final class TeamViewModel: ObservableObject {
    @Published var teams: [TeamInfo] = []
    @Published var currentTeam: TeamInfo?
    @Published var members: [TeamMemberInfo] = []
    @Published var teamShifts: [TeamShiftInfo] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var userRole: String = "member"
    @Published var memberJobs: [MemberJobInfo] = []
    @Published var teamMessages: [TeamMessageInfo] = []
    @Published var swapRequests: [SwapRequestInfo] = []
    @Published var teamTasks: [TeamTaskInfo] = []

    @Published var isUploadingImage = false

    private let db = Firestore.firestore(database: "schedulo2")
    private let storageRef = Storage.storage().reference()
    private var teamsListener: ListenerRegistration?
    private var membersListener: ListenerRegistration?
    private var shiftsListener: ListenerRegistration?
    private var memberJobsListener: ListenerRegistration?
    private var messagesListener: ListenerRegistration?
    private var swapRequestsListener: ListenerRegistration?
    private var teamTasksListener: ListenerRegistration?
    private var scheduleNotificationsListener: ListenerRegistration?

    var currentUserId: String? { Auth.auth().currentUser?.uid }
    var currentUserEmail: String? { Auth.auth().currentUser?.email }

    var isManager: Bool { userRole == "manager" }

    func fetchMemberJobs(userId: String) {
        memberJobsListener?.remove()
        memberJobsListener = db.collection("jobs")
            .whereField("userId", isEqualTo: userId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let documents = snapshot?.documents else {
                    self?.memberJobs = []
                    return
                }
                self?.memberJobs = documents.compactMap { doc in
                    let data = doc.data()
                    return MemberJobInfo(
                        id: doc.documentID,
                        title: data["title"] as? String ?? "",
                        defaultHourlyRate: data["defaultHourlyRate"] as? Double ?? 15.0,
                        isGigWork: data["isGigWork"] as? Bool ?? false
                    )
                }
            }
    }

    func loadTeams() {
        guard let uid = currentUserId else { return }
        teamsListener?.remove()

        teamsListener = db.collection("team_members")
            .whereField("userId", isEqualTo: uid)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let docs = snapshot?.documents, error == nil else { return }
                let teamIds = docs.compactMap { $0.data()["teamId"] as? String }
                guard !teamIds.isEmpty else {
                    self?.teams = []
                    return
                }
                self?.fetchTeams(ids: teamIds)
            }
    }

    private func fetchTeams(ids: [String]) {
        let group = DispatchGroup()
        var fetched: [TeamInfo] = []

        for id in ids {
            group.enter()
            db.collection("teams").document(id).getDocument { snapshot, _ in
                defer { group.leave() }
                guard let data = snapshot?.data() else { return }
                let team = TeamInfo(
                    id: snapshot?.documentID ?? id,
                    name: data["name"] as? String ?? "",
                    ownerId: data["ownerId"] as? String ?? "",
                    inviteCode: data["inviteCode"] as? String ?? "",
                    createdAt: data["createdAt"] as? Int64 ?? 0,
                    memberCount: data["memberCount"] as? Int ?? 0,
                    weeklyCycleStartDay: data["weeklyCycleStartDay"] as? String ?? "Monday",
                    companyName: data["companyName"] as? String ?? "",
                    open24Hours: data["open24Hours"] as? Bool ?? true,
                    workStartMinutes: data["workStartMinutes"] as? Int ?? 0,
                    workEndMinutes: data["workEndMinutes"] as? Int ?? 0,
                    addressLine: data["addressLine"] as? String ?? "",
                    city: data["city"] as? String ?? "",
                    region: data["region"] as? String ?? "",
                    postalCode: data["postalCode"] as? String ?? ""
                )
                fetched.append(team)
            }
        }

        group.notify(queue: .main) { [weak self] in
            self?.teams = fetched.sorted { $0.name < $1.name }
            if self?.currentTeam == nil, let first = fetched.first {
                self?.selectTeam(first)
            }
        }
    }

    func selectTeam(_ team: TeamInfo) {
        currentTeam = team
        loadMembers(teamId: team.id)
        loadTeamShifts(teamId: team.id)
        loadTeamMessages(teamId: team.id)
        loadSwapRequests(teamId: team.id)
        loadTeamTasks(teamId: team.id)
        updateUserRole(teamId: team.id)
    }

    private func updateUserRole(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                if let doc = snapshot?.documents.first {
                    self?.userRole = doc.data()["role"] as? String ?? "member"
                }
            }
    }

    func loadMembers(teamId: String) {
        membersListener?.remove()
        membersListener = db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.members = docs.map { doc in
                    let data = doc.data()
                    return TeamMemberInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        userId: data["userId"] as? String ?? "",
                        role: data["role"] as? String ?? "member",
                        joinedAt: data["joinedAt"] as? Int64 ?? 0,
                        displayName: data["displayName"] as? String ?? "",
                        email: data["email"] as? String ?? "",
                        defaultHourlyRate: data["defaultHourlyRate"] as? Double ?? 0.0
                    )
                }.sorted { $0.displayName < $1.displayName }
            }
    }

    func loadTeamShifts(teamId: String) {
        shiftsListener?.remove()
        shiftsListener = db.collection("team_shifts")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.teamShifts = docs.map { doc in
                    let data = doc.data()
                    let tasksRaw = data["tasks"] as? [[String: Any]] ?? []
                    let parsedTasks = tasksRaw.map { t in
                        ShiftTaskInfo(
                            id: t["id"] as? String ?? UUID().uuidString,
                            title: t["title"] as? String ?? "",
                            isCompleted: t["isCompleted"] as? Bool ?? false
                        )
                    }
                    return TeamShiftInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        assignedTo: data["assignedTo"] as? String ?? "",
                        assignedBy: data["assignedBy"] as? String ?? "",
                        company: data["company"] as? String ?? "",
                        role: data["role"] as? String ?? "",
                        startTime: (data["startTime"] as? NSNumber)?.int64Value ?? 0,
                        endTime: (data["endTime"] as? NSNumber)?.int64Value ?? 0,
                        hourlyRate: data["hourlyRate"] as? Double ?? 0.0,
                        notes: data["notes"] as? String ?? "",
                        // Schedules are approved on assignment now; treat legacy
                        // "assigned" docs from older builds as accepted.
                        status: {
                            let raw = data["status"] as? String ?? "accepted"
                            return raw == "assigned" ? "accepted" : raw
                        }(),
                        tasks: parsedTasks
                    )
                }.sorted { $0.startTime > $1.startTime }
                if let shifts = self?.teamShifts {
                    self?.reconcilePersonalMirrors(teamShifts: shifts, teamId: teamId)
                }
            }
    }

    // MARK: - Team CRUD

    func createTeam(form: TeamFormData) {
        guard let uid = currentUserId else { return }
        guard Auth.auth().currentUser?.isEmailVerified == true else {
            errorMessage = "Please verify your email address before creating a team. Check your inbox for the verification link."
            return
        }
        guard !form.name.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Team name cannot be empty."; return
        }
        guard !form.companyName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Company name cannot be empty."; return
        }
        isLoading = true
        let teamId = UUID().uuidString
        let inviteCode = generateInviteCode()
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var teamData: [String: Any] = [
            "name": form.name.trimmingCharacters(in: .whitespaces),
            "ownerId": uid,
            "inviteCode": inviteCode,
            "createdAt": now,
            "memberCount": 1
        ]
        teamData.merge(form.firestoreFields()) { _, new in new }

        let memberData: [String: Any] = [
            "teamId": teamId,
            "userId": uid,
            "role": "manager",
            "joinedAt": now,
            "displayName": currentUserEmail ?? "",
            "email": currentUserEmail ?? "",
            "defaultHourlyRate": 0.0
        ]

        let batch = db.batch()
        batch.setData(teamData, forDocument: db.collection("teams").document(teamId))
        // Deterministic membership id "{teamId}_{userId}" lets security rules prove
        // membership with a single exists() check.
        batch.setData(memberData, forDocument: db.collection("team_members").document("\(teamId)_\(uid)"))
        // Public-by-secret lookup so joiners can resolve a code -> teamId without
        // the teams collection being world-readable.
        batch.setData(["teamId": teamId], forDocument: db.collection("invite_codes").document(inviteCode))

        batch.commit { [weak self] error in
            DispatchQueue.main.async {
                self?.isLoading = false
                if let error = error {
                    self?.errorMessage = error.localizedDescription
                } else {
                    self?.loadTeams()
                }
            }
        }
    }

    /// Update the editable team fields (manager/owner only, enforced by rules).
    func updateTeam(teamId: String, form: TeamFormData) {
        guard !form.name.trimmingCharacters(in: .whitespaces).isEmpty,
              !form.companyName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Team and company name cannot be empty."; return
        }
        var updates: [String: Any] = ["name": form.name.trimmingCharacters(in: .whitespaces)]
        updates.merge(form.firestoreFields()) { _, new in new }
        db.collection("teams").document(teamId).updateData(updates) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.errorMessage = "Failed to update team: \(error.localizedDescription)"
                } else if self?.currentTeam?.id == teamId {
                    self?.currentTeam?.name = form.name.trimmingCharacters(in: .whitespaces)
                    self?.currentTeam?.companyName = form.companyName.trimmingCharacters(in: .whitespaces)
                    self?.currentTeam?.weeklyCycleStartDay = form.weeklyCycleStartDay
                    self?.currentTeam?.open24Hours = form.open24Hours
                    self?.currentTeam?.workStartMinutes = form.workStartMinutes
                    self?.currentTeam?.workEndMinutes = form.workEndMinutes
                    self?.currentTeam?.addressLine = form.addressLine.trimmingCharacters(in: .whitespaces)
                    self?.currentTeam?.city = form.city.trimmingCharacters(in: .whitespaces)
                    self?.currentTeam?.region = form.region.trimmingCharacters(in: .whitespaces)
                    self?.currentTeam?.postalCode = form.postalCode.trimmingCharacters(in: .whitespaces)
                }
            }
        }
    }

    /// Set a member's default pay rate (manager/owner only).
    func updateMemberRate(memberDocId: String, rate: Double) {
        let safeRate = max(0, rate)
        let previous = members
        members = members.map { m in
            var m = m
            if m.id == memberDocId { m.defaultHourlyRate = safeRate }
            return m
        }
        db.collection("team_members").document(memberDocId).updateData(["defaultHourlyRate": safeRate]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to update pay rate: \(error.localizedDescription)"
                }
            }
        }
    }

    func joinTeam(inviteCode: String) {
        guard let uid = currentUserId else { return }
        guard Auth.auth().currentUser?.isEmailVerified == true else {
            errorMessage = "Please verify your email address before joining a team. Check your inbox for the verification link."
            return
        }
        isLoading = true
        let code = inviteCode.uppercased()

        // Resolve the code -> teamId via the public-by-secret lookup collection
        // (a direct get() by document id; the teams collection is not queryable
        // by non-members).
        db.collection("invite_codes").document(code)
            .getDocument { [weak self] snapshot, error in
                guard let self = self else { return }
                guard let teamId = snapshot?.data()?["teamId"] as? String, !teamId.isEmpty else {
                    DispatchQueue.main.async {
                        self.isLoading = false
                        self.errorMessage = "Invalid invite code"
                    }
                    return
                }

                let memberRef = self.db.collection("team_members").document("\(teamId)_\(uid)")

                // Check if already a member (direct read of the deterministic doc).
                memberRef.getDocument { existingSnapshot, _ in
                    if existingSnapshot?.exists == true {
                        DispatchQueue.main.async {
                            self.isLoading = false
                            self.errorMessage = "You are already a member of this team."
                        }
                        return
                    }

                    let memberData: [String: Any] = [
                        "teamId": teamId,
                        "userId": uid,
                        "role": "member",
                        "joinedAt": Int64(Date().timeIntervalSince1970 * 1000),
                        "displayName": self.currentUserEmail ?? "",
                        "email": self.currentUserEmail ?? "",
                        // Included so the security rule can verify the joiner
                        // presented the correct invite code.
                        "inviteCode": code
                    ]

                    let batch = self.db.batch()
                    batch.setData(memberData, forDocument: memberRef)
                    batch.updateData(["memberCount": FieldValue.increment(Int64(1))], forDocument: self.db.collection("teams").document(teamId))

                    batch.commit { error in
                        DispatchQueue.main.async {
                            self.isLoading = false
                            if let error = error {
                                self.errorMessage = error.localizedDescription
                            } else {
                                self.loadTeams()
                            }
                        }
                    }
                }
            }
    }

    func assignShift(to memberId: String, company: String, role: String, startTime: Int64, endTime: Int64, hourlyRate: Double, notes: String, tasks: [ShiftTaskInfo] = []) {
        guard let teamId = currentTeam?.id, let uid = currentUserId else { return }
        let shiftId = UUID().uuidString

        let tasksData = tasks.map { t -> [String: Any] in
            ["id": t.id, "title": t.title, "isCompleted": t.isCompleted]
        }

        let data: [String: Any] = [
            "teamId": teamId,
            "assignedTo": memberId,
            "assignedBy": uid,
            "company": company,
            "role": role,
            "startTime": startTime,
            "endTime": endTime,
            "hourlyRate": hourlyRate,
            "notes": notes,
            "status": "accepted",
            "tasks": tasksData
        ]

        db.collection("team_shifts").document(shiftId).setData(data) { [weak self] error in
            if error == nil {
                let teamShift = TeamShiftInfo(
                    id: shiftId,
                    teamId: teamId,
                    assignedTo: memberId,
                    assignedBy: uid,
                    company: company,
                    role: role,
                    startTime: startTime,
                    endTime: endTime,
                    hourlyRate: hourlyRate,
                    notes: notes,
                    status: "accepted",
                    tasks: tasks
                )
                self?.createPersonalShiftFromTeam(teamShift, targetUserId: memberId)
                self?.notifyScheduleAssigned(teamShift, targetUserId: memberId)
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    /// Writes a notifications doc so the assignee's device can surface the new schedule.
    private func notifyScheduleAssigned(_ teamShift: TeamShiftInfo, targetUserId: String) {
        guard let uid = currentUserId, targetUserId != uid else { return }
        let data: [String: Any] = [
            "userId": targetUserId,
            "type": "shift_assigned",
            "teamId": teamShift.teamId,
            "teamShiftId": teamShift.id,
            "teamName": currentTeam?.name ?? "",
            "company": teamShift.company,
            "startTime": teamShift.startTime,
            "endTime": teamShift.endTime,
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000),
            "read": false
        ]
        // Best-effort: the schedule itself is already saved; a failed notification
        // write must not surface as an assignment error.
        db.collection("notifications").document().setData(data)
    }

    /// Global listener for schedule-assignment notifications addressed to the
    /// current user. Unlike the team listeners (which only run while a team is
    /// selected), this runs for the whole signed-in session, so assignments land
    /// as a device notification no matter which screen is open. A persisted
    /// createdAt watermark stops docs from re-firing on every cold start.
    func startScheduleNotificationsListener() {
        guard let uid = currentUserId else { return }
        let watermarkKey = "scheduleNotificationsLastSeen_\(uid)"
        let defaults = UserDefaults.standard

        scheduleNotificationsListener?.remove()
        scheduleNotificationsListener = db.collection("notifications")
            .whereField("userId", isEqualTo: uid)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self = self, let docs = snapshot?.documents else { return }
                // Default the watermark to "now" on first run so a fresh install
                // doesn't replay the full history as banners.
                if defaults.object(forKey: watermarkKey) == nil {
                    defaults.set(Int64(Date().timeIntervalSince1970 * 1000), forKey: watermarkKey)
                }
                var watermark = Int64(defaults.double(forKey: watermarkKey))
                let newDocs = docs
                    .filter { (($0.data()["createdAt"] as? NSNumber)?.int64Value ?? 0) > watermark }
                    .sorted { (($0.data()["createdAt"] as? NSNumber)?.int64Value ?? 0) < (($1.data()["createdAt"] as? NSNumber)?.int64Value ?? 0) }
                for doc in newDocs {
                    let data = doc.data()
                    self.postScheduleNotification(
                        teamName: data["teamName"] as? String ?? "",
                        company: data["company"] as? String ?? "",
                        startTime: (data["startTime"] as? NSNumber)?.int64Value ?? 0,
                        endTime: (data["endTime"] as? NSNumber)?.int64Value ?? 0
                    )
                    watermark = max(watermark, (data["createdAt"] as? NSNumber)?.int64Value ?? 0)
                }
                if !newDocs.isEmpty {
                    defaults.set(Double(watermark), forKey: watermarkKey)
                }
            }
    }

    private func postScheduleNotification(teamName: String, company: String, startTime: Int64, endTime: Int64) {
        let startDate = Date(timeIntervalSince1970: Double(startTime) / 1000.0)
        let endDate = Date(timeIntervalSince1970: Double(endTime) / 1000.0)
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "EEE, MMM dd · h:mm a"
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        let sameDay = Calendar.current.isDate(startDate, inSameDayAs: endDate)
        let endLabel = sameDay ? timeFormatter.string(from: endDate) : dateFormatter.string(from: endDate)

        let content = UNMutableNotificationContent()
        content.title = teamName.isEmpty ? "New shift scheduled" : "New shift scheduled — \(teamName)"
        content.body = "\(company): \(dateFormatter.string(from: startDate)) – \(endLabel)"
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func toggleTaskCompletion(shiftId: String, taskId: String) {
        let ref = db.collection("team_shifts").document(shiftId)
        ref.getDocument { [weak self] snapshot, _ in
            guard let data = snapshot?.data(),
                  var tasks = data["tasks"] as? [[String: Any]] else { return }
            if let idx = tasks.firstIndex(where: { $0["id"] as? String == taskId }) {
                let current = tasks[idx]["isCompleted"] as? Bool ?? false
                tasks[idx]["isCompleted"] = !current
                if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                   let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                    DispatchQueue.main.async {
                        self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = !current
                    }
                }
                ref.updateData(["tasks": tasks]) { error in
                    if let error = error {
                        DispatchQueue.main.async {
                            self?.errorMessage = error.localizedDescription
                            if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                               let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                                self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = current
                            }
                        }
                    }
                }
            }
        }
    }

    private func createPersonalShiftFromTeam(_ teamShift: TeamShiftInfo, targetUserId: String) {
        let shiftData: [String: Any] = [
            "userId": targetUserId,
            "company": teamShift.company,
            "role": teamShift.role,
            "startTime": teamShift.startTime,
            "endTime": teamShift.endTime,
            "hourlyRate": teamShift.hourlyRate,
            "isGig": false,
            "customEarned": 0.0,
            "reminderBeforeMinutes": 30,
            "isPaid": false,
            "notes": "Team shift: \(teamShift.notes)".trimmingCharacters(in: .whitespaces),
            "bonusApplied": false,
            "bonusAmount": 0.0,
            "teamShiftId": teamShift.id,
            "teamId": teamShift.teamId
        ]
        // Deterministic doc id so concurrent mirror creation (assign + reconcile,
        // or two devices) collapses into one document instead of duplicates.
        db.collection("shifts").document("team_\(teamShift.id)").setData(shiftData) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.errorMessage = error.localizedDescription
                }
            }
        }
    }

    /// Keeps the current user's personal mirror copies in sync with the selected
    /// team's shifts. Swaps only update team_shifts (assignedTo/hourlyRate), so
    /// without this the old assignee keeps a stale mirror and the new assignee
    /// never gets one. Operates only on the user's own docs (rules-compatible)
    /// and only on mirrors tied to this team, so other teams' mirrors are untouched.
    private func reconcilePersonalMirrors(teamShifts: [TeamShiftInfo], teamId: String) {
        guard let uid = currentUserId else { return }
        let shiftsById = Dictionary(uniqueKeysWithValues: teamShifts.map { ($0.id, $0) })

        db.collection("shifts").whereField("userId", isEqualTo: uid).getDocuments { [weak self] snapshot, _ in
            guard let self = self, let docs = snapshot?.documents else { return }
            let mirrors = docs.filter { !(($0.data()["teamShiftId"] as? String) ?? "").isEmpty }
            let mirroredTeamShiftIds = Set(mirrors.compactMap { $0.data()["teamShiftId"] as? String })

            for doc in mirrors {
                let data = doc.data()
                guard let teamShiftId = data["teamShiftId"] as? String else { continue }
                let teamShift = shiftsById[teamShiftId]
                let mirrorTeamId = data["teamId"] as? String ?? ""
                let orphaned = teamShift == nil && mirrorTeamId == teamId
                let reassigned = teamShift != nil && teamShift?.assignedTo != uid
                if orphaned || reassigned {
                    doc.reference.delete()
                    NotificationService.shared.cancelReminder(shiftId: doc.documentID)
                }
            }

            for teamShift in teamShifts
            where teamShift.assignedTo == uid && teamShift.status != "declined" && !mirroredTeamShiftIds.contains(teamShift.id) {
                self.createPersonalShiftFromTeam(teamShift, targetUserId: uid)
            }
        }
    }

    func updateTeamName(teamId: String, newName: String) {
        db.collection("teams").document(teamId).updateData(["name": newName]) { [weak self] error in
            if error == nil {
                DispatchQueue.main.async {
                    self?.loadTeams()
                }
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func updateWeeklyCycleStartDay(teamId: String, day: String) {
        db.collection("teams").document(teamId).updateData(["weeklyCycleStartDay": day]) { [weak self] error in
            if error == nil {
                DispatchQueue.main.async {
                    if self?.currentTeam?.id == teamId {
                        self?.currentTeam?.weeklyCycleStartDay = day
                    }
                }
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func deleteTeam(teamId: String) {
        isLoading = true
        let group = DispatchGroup()
        var deleteError: Error?

        group.enter()
        db.collection("team_members").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.enter()
        db.collection("team_shifts").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.notify(queue: .main) { [weak self] in
            if let deleteError = deleteError {
                self?.isLoading = false
                self?.errorMessage = deleteError.localizedDescription
                return
            }
            // Remove the invite-code lookup entry alongside the team.
            if let inviteCode = self?.currentTeam?.inviteCode,
               self?.currentTeam?.id == teamId, !inviteCode.isEmpty {
                self?.db.collection("invite_codes").document(inviteCode).delete()
            }
            self?.db.collection("teams").document(teamId).delete { error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    if let error = error {
                        self?.errorMessage = error.localizedDescription
                        return
                    }
                    self?.currentTeam = nil
                    self?.members = []
                    self?.teamShifts = []
                    self?.loadTeams()
                }
            }
        }
    }

    func deleteTeamShift(shiftId: String) {
        teamShifts = teamShifts.filter { $0.id != shiftId }
        db.collection("shifts").whereField("teamShiftId", isEqualTo: shiftId).getDocuments { [weak self] snapshot, _ in
            snapshot?.documents.forEach { $0.reference.delete() }
        }
        db.collection("team_shifts").document(shiftId).delete()
    }

    func promoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "manager"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "manager"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to promote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func demoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "member"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "member"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to demote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func removeMember(memberDocId: String, teamId: String) {
        let previous = members
        members.removeAll { $0.id == memberDocId }

        let batch = db.batch()
        batch.deleteDocument(db.collection("team_members").document(memberDocId))
        batch.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: db.collection("teams").document(teamId))

        batch.commit { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to remove member: \(error.localizedDescription)"
                }
            }
        }
    }

    func loadTeamMessages(teamId: String) {
        // No server-side .order(by:) here: combining the teamId filter with an
        // ordering needs a composite index, and a missing index makes the listener
        // fail silently (messages arrive late / no notification). We sort locally.
        messagesListener?.remove()
        messagesListener = db.collection("team_messages")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                let uid = self?.currentUserId
                let oldIds: Set<String> = Set(self?.teamMessages.map { $0.id } ?? [])
                let newMessages: [TeamMessageInfo] = docs.map { doc in
                    let data = doc.data()
                    return TeamMessageInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        senderId: data["senderId"] as? String ?? "",
                        senderName: data["senderName"] as? String ?? "",
                        text: data["text"] as? String ?? "",
                        isAnnouncement: data["isAnnouncement"] as? Bool ?? false,
                        isPinned: data["isPinned"] as? Bool ?? false,
                        imageUrl: data["imageUrl"] as? String ?? "",
                        seenBy: data["seenBy"] as? [String] ?? [],
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0
                    )
                }
                if !oldIds.isEmpty {
                    for msg in newMessages.sorted(by: { $0.createdAt < $1.createdAt })
                    where !oldIds.contains(msg.id) && msg.senderId != uid {
                        let preview = msg.imageUrl.isEmpty ? msg.text : "Sent a photo"
                        self?.postChatNotification(sender: msg.senderName, body: preview)
                    }
                }
                self?.teamMessages = newMessages.sorted { $0.createdAt > $1.createdAt }
            }
    }

    private func postChatNotification(sender: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = "Team Chat – \(currentTeam?.name ?? "Team")"
        content.body = "\(sender): \(body)"
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func sendMessage(text: String, isAnnouncement: Bool = false) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let senderName = currentUserEmail ?? ""
        let data: [String: Any] = [
            "teamId": teamId,
            "senderId": uid,
            "senderName": senderName,
            "text": trimmed,
            "isAnnouncement": isAnnouncement,
            "isPinned": false,
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000)
        ]

        db.collection("team_messages").document(UUID().uuidString).setData(data) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.errorMessage = "Failed to send message: \(error.localizedDescription)"
                }
            }
        }
    }

    func sendImage(_ image: UIImage) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }
        isUploadingImage = true

        let senderName = currentUserEmail ?? ""
        let messageId = UUID().uuidString
        let maxDim: CGFloat = 800
        let scale: CGFloat = min(maxDim / image.size.width, maxDim / image.size.height, 1)
        let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resized: UIImage = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
        guard let data = resized.jpegData(compressionQuality: 0.5) else {
            isUploadingImage = false
            errorMessage = "Failed to compress image"
            return
        }

        let ref = storageRef.child("chat_images/\(teamId)/\(messageId).jpg")
        ref.putData(data, metadata: nil) { [weak self] _, error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.isUploadingImage = false
                    self?.errorMessage = "Upload failed: \(error.localizedDescription)"
                }
                return
            }
            ref.downloadURL { [weak self] url, _ in
                DispatchQueue.main.async { self?.isUploadingImage = false }
                guard let downloadUrl = url else { return }
                let msgData: [String: Any] = [
                    "teamId": teamId,
                    "senderId": uid,
                    "senderName": senderName,
                    "text": "",
                    "isAnnouncement": false,
                    "isPinned": false,
                    "imageUrl": downloadUrl.absoluteString,
                    "seenBy": [uid],
                    "createdAt": Int64(Date().timeIntervalSince1970 * 1000)
                ]
                self?.db.collection("team_messages").document(messageId).setData(msgData)
            }
        }
    }

    func markMessageSeen(messageId: String) {
        guard let uid = currentUserId else { return }
        let message = teamMessages.first { $0.id == messageId }
        guard let msg = message, !msg.seenBy.contains(uid) else { return }
        db.collection("team_messages").document(messageId).updateData([
            "seenBy": FieldValue.arrayUnion([uid])
        ]) { [weak self] _ in
            self?.checkAutoDelete(messageId: messageId)
        }
    }

    private func checkAutoDelete(messageId: String) {
        let memberCount = members.count
        guard memberCount > 0 else { return }
        db.collection("team_messages").document(messageId).getDocument { [weak self] doc, _ in
            guard let data = doc?.data() else { return }
            let seenBy = data["seenBy"] as? [String] ?? []
            let imageUrl = data["imageUrl"] as? String ?? ""
            if !imageUrl.isEmpty && seenBy.count >= memberCount {
                Storage.storage().reference(forURL: imageUrl).delete(completion: nil)
                self?.db.collection("team_messages").document(messageId).updateData([
                    "imageUrl": "", "text": "Image expired"
                ])
            }
        }
    }

    func deleteMessage(messageId: String) {
        let previous = teamMessages
        let deleted = previous.first { $0.id == messageId }
        if let url = deleted?.imageUrl, !url.isEmpty {
            Storage.storage().reference(forURL: url).delete(completion: nil)
        }
        teamMessages.removeAll { $0.id == messageId }
        db.collection("team_messages").document(messageId).delete { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamMessages = previous
                    self?.errorMessage = "Failed to delete: \(error.localizedDescription)"
                }
            }
        }
    }

    func togglePin(messageId: String) {
        guard let idx = teamMessages.firstIndex(where: { $0.id == messageId }) else { return }
        let previous = teamMessages
        let newPinned = !teamMessages[idx].isPinned
        teamMessages[idx].isPinned = newPinned
        db.collection("team_messages").document(messageId).updateData(["isPinned": newPinned]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamMessages = previous
                    self?.errorMessage = "Failed to update pin: \(error.localizedDescription)"
                }
            }
        }
    }

    func loadSwapRequests(teamId: String) {
        swapRequestsListener?.remove()
        swapRequestsListener = db.collection("swap_requests")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.swapRequests = docs.map { doc in
                    let data = doc.data()
                    return SwapRequestInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        requesterId: data["requesterId"] as? String ?? "",
                        requesterName: data["requesterName"] as? String ?? "",
                        requesterShiftId: data["requesterShiftId"] as? String ?? "",
                        targetMemberId: data["targetMemberId"] as? String ?? "",
                        targetMemberName: data["targetMemberName"] as? String ?? "",
                        targetShiftId: data["targetShiftId"] as? String ?? "",
                        status: data["status"] as? String ?? "pending",
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0,
                        resolvedAt: (data["resolvedAt"] as? NSNumber)?.int64Value ?? 0,
                        resolvedBy: data["resolvedBy"] as? String ?? ""
                    )
                }.sorted { $0.createdAt > $1.createdAt }
            }
    }

    // MARK: - Standalone team tasks (assigned to a member, with progress + history)

    func loadTeamTasks(teamId: String) {
        teamTasksListener?.remove()
        teamTasksListener = db.collection("team_tasks")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    DispatchQueue.main.async { self?.errorMessage = "Failed to load tasks: \(error.localizedDescription)" }
                    return
                }
                guard let docs = snapshot?.documents else { return }
                self?.teamTasks = docs.map { doc in
                    let data = doc.data()
                    let historyRaw = data["history"] as? [[String: Any]] ?? []
                    let history: [TaskHistoryEntryInfo] = historyRaw.map { item in
                        TaskHistoryEntryInfo(
                            status: item["status"] as? String ?? "",
                            changedBy: item["changedBy"] as? String ?? "",
                            changedByName: item["changedByName"] as? String ?? "",
                            timestamp: (item["timestamp"] as? NSNumber)?.int64Value ?? 0
                        )
                    }.sorted { $0.timestamp > $1.timestamp }
                    return TeamTaskInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        title: data["title"] as? String ?? "",
                        taskDescription: data["description"] as? String ?? "",
                        assignedTo: data["assignedTo"] as? String ?? "",
                        assignedToName: data["assignedToName"] as? String ?? "",
                        assignedBy: data["assignedBy"] as? String ?? "",
                        status: data["status"] as? String ?? "pending",
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? 0,
                        updatedAt: (data["updatedAt"] as? NSNumber)?.int64Value ?? 0,
                        history: history
                    )
                }.sorted { $0.createdAt > $1.createdAt }
            }
    }

    func createTeamTask(memberId: String, memberName: String, title: String, description: String) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else {
            errorMessage = "Task title is required."
            return
        }
        guard !memberId.isEmpty else {
            errorMessage = "Select a member to assign the task to."
            return
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let actorName = currentUserEmail ?? ""
        let data: [String: Any] = [
            "teamId": teamId,
            "title": trimmedTitle,
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "assignedTo": memberId,
            "assignedToName": memberName,
            "assignedBy": uid,
            "status": "pending",
            "createdAt": now,
            "updatedAt": now,
            "history": [[
                "status": "pending",
                "changedBy": uid,
                "changedByName": actorName,
                "timestamp": now
            ]]
        ]
        db.collection("team_tasks").document(UUID().uuidString).setData(data) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to create task: \(error.localizedDescription)" }
            }
        }
    }

    func updateTeamTaskStatus(taskId: String, newStatus: String) {
        guard let uid = currentUserId else { return }
        guard let task = teamTasks.first(where: { $0.id == taskId }), task.status != newStatus else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let actorName = currentUserEmail ?? ""
        let entry: [String: Any] = [
            "status": newStatus,
            "changedBy": uid,
            "changedByName": actorName,
            "timestamp": now
        ]
        db.collection("team_tasks").document(taskId).updateData([
            "status": newStatus,
            "updatedAt": now,
            "history": FieldValue.arrayUnion([entry])
        ]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to update task: \(error.localizedDescription)" }
            }
        }
    }

    func deleteTeamTask(taskId: String) {
        let previous = teamTasks
        teamTasks.removeAll { $0.id == taskId }
        db.collection("team_tasks").document(taskId).delete { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamTasks = previous
                    self?.errorMessage = "Failed to delete task: \(error.localizedDescription)"
                }
            }
        }
    }

    func requestSwap(myShiftId: String, targetMemberId: String, targetShiftId: String) {
        guard let uid = currentUserId, let teamId = currentTeam?.id else { return }

        let requesterName = currentUserEmail ?? ""
        let targetMember = members.first { $0.userId == targetMemberId }
        let targetName = targetMember?.displayName.isEmpty == false ? targetMember!.displayName : (targetMember?.email ?? "")

        let data: [String: Any] = [
            "teamId": teamId,
            "requesterId": uid,
            "requesterName": requesterName,
            "requesterShiftId": myShiftId,
            "targetMemberId": targetMemberId,
            "targetMemberName": targetName,
            "targetShiftId": targetShiftId,
            "status": "pending",
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000),
            "resolvedAt": Int64(0),
            "resolvedBy": ""
        ]

        db.collection("swap_requests").document(UUID().uuidString).setData(data) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to request swap: \(error.localizedDescription)" }
            }
        }
    }

    func respondToSwap(requestId: String, accept: Bool) {
        guard let uid = currentUserId else { return }
        let newStatus = accept ? "target_accepted" : "declined"
        let previous = swapRequests
        if let idx = swapRequests.firstIndex(where: { $0.id == requestId }) {
            swapRequests[idx].status = newStatus
        }
        var updates: [String: Any] = ["status": newStatus]
        if !accept {
            updates["resolvedBy"] = uid
            updates["resolvedAt"] = Int64(Date().timeIntervalSince1970 * 1000)
        }
        db.collection("swap_requests").document(requestId).updateData(updates) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.swapRequests = previous
                    self?.errorMessage = "Failed to respond: \(error.localizedDescription)"
                }
            }
        }
    }

    func approveSwap(requestId: String, approve: Bool) {
        guard let uid = currentUserId else { return }
        if !approve {
            let previous = swapRequests
            if let idx = swapRequests.firstIndex(where: { $0.id == requestId }) {
                swapRequests[idx].status = "declined"
            }
            db.collection("swap_requests").document(requestId).updateData([
                "status": "declined",
                "resolvedBy": uid,
                "resolvedAt": Int64(Date().timeIntervalSince1970 * 1000)
            ]) { [weak self] error in
                if let error = error {
                    DispatchQueue.main.async {
                        self?.swapRequests = previous
                        self?.errorMessage = "Failed to decline: \(error.localizedDescription)"
                    }
                }
            }
            return
        }

        guard let request = swapRequests.first(where: { $0.id == requestId }) else { return }
        executeSwap(request)
    }

    private func executeSwap(_ request: SwapRequestInfo) {
        guard let uid = currentUserId else { return }

        // A swap trades only the time-slots, never the pay rate. Each person's hourly
        // rate must move with them, otherwise whoever takes a shift would be paid the
        // other person's rate. Read both shifts' current rates and swap them alongside
        // assignedTo so nobody's pay rate changes because of the swap.
        guard let requesterShift = teamShifts.first(where: { $0.id == request.requesterShiftId }),
              let targetShift = teamShifts.first(where: { $0.id == request.targetShiftId }) else {
            errorMessage = "Couldn't load the shift details to swap. Please try again."
            return
        }
        let requesterRate = requesterShift.hourlyRate
        let targetRate = targetShift.hourlyRate

        let batch = db.batch()
        batch.updateData(
            ["assignedTo": request.targetMemberId, "hourlyRate": targetRate],
            forDocument: db.collection("team_shifts").document(request.requesterShiftId)
        )
        batch.updateData(
            ["assignedTo": request.requesterId, "hourlyRate": requesterRate],
            forDocument: db.collection("team_shifts").document(request.targetShiftId)
        )
        batch.updateData([
            "status": "approved",
            "resolvedBy": uid,
            "resolvedAt": Int64(Date().timeIntervalSince1970 * 1000)
        ], forDocument: db.collection("swap_requests").document(request.id))

        batch.commit { [weak self] error in
            if let error = error {
                DispatchQueue.main.async { self?.errorMessage = "Failed to execute swap: \(error.localizedDescription)" }
            }
        }
    }

    func cancelSwapRequest(requestId: String) {
        let previous = swapRequests
        swapRequests.removeAll { $0.id == requestId }
        db.collection("swap_requests").document(requestId).delete { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.swapRequests = previous
                    self?.errorMessage = "Failed to cancel: \(error.localizedDescription)"
                }
            }
        }
    }

    func leaveTeam(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                guard let doc = snapshot?.documents.first else { return }
                let batch = self?.db.batch()
                batch?.deleteDocument(doc.reference)
                batch?.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: self?.db.collection("teams").document(teamId) ?? self!.db.collection("teams").document(teamId))
                batch?.commit { error in
                    DispatchQueue.main.async {
                        if let error = error {
                            self?.errorMessage = error.localizedDescription
                            return
                        }
                        self?.currentTeam = nil
                        self?.loadTeams()
                    }
                }
            }
    }

    /// Detach every Firestore listener and clear all team state. Must be called
    /// on sign-out / delete-account: this view model lives for the whole app
    /// lifetime, so without this the previous account's team data (chat, shifts,
    /// roster) keeps streaming into a still-alive view model after a switch.
    func removeAllListeners() {
        teamsListener?.remove(); teamsListener = nil
        membersListener?.remove(); membersListener = nil
        shiftsListener?.remove(); shiftsListener = nil
        memberJobsListener?.remove(); memberJobsListener = nil
        messagesListener?.remove(); messagesListener = nil
        swapRequestsListener?.remove(); swapRequestsListener = nil
        teamTasksListener?.remove(); teamTasksListener = nil
        scheduleNotificationsListener?.remove(); scheduleNotificationsListener = nil

        teams = []
        currentTeam = nil
        members = []
        teamShifts = []
        memberJobs = []
        teamMessages = []
        swapRequests = []
        teamTasks = []
        userRole = "member"
        errorMessage = nil
        isLoading = false
    }

    deinit {
        teamsListener?.remove()
        membersListener?.remove()
        shiftsListener?.remove()
        memberJobsListener?.remove()
        messagesListener?.remove()
        swapRequestsListener?.remove()
        teamTasksListener?.remove()
        scheduleNotificationsListener?.remove()
    }

    private func generateInviteCode() -> String {
        let chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return String((0..<6).map { _ in chars.randomElement()! })
    }
}
