import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @EnvironmentObject var teamViewModel: TeamViewModel

    @State private var selectedTab = 0
    @State private var showAddMenu = false
    @State private var showAddShift = false
    @State private var showWeekPlan = false
    @State private var showProfile = false
    @State private var showInsights = false
    @State private var showJobsView = false
    @State private var editingShiftId: String?
    @Namespace private var tabNamespace

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch selectedTab {
                case 0:
                    DashboardView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onNavigateToProfile: { showProfile = true },
                        onNavigateToPay: { selectedTab = 2 }
                    )
                    .transition(.opacity)
                case 1:
                    PlanView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onAddShift: { editingShiftId = nil; showAddShift = true }
                    )
                    .transition(.opacity)
                case 2:
                    PayView()
                        .transition(.opacity)
                case 3:
                    TeamView()
                        .transition(.opacity)
                default:
                    EmptyView()
                }
            }
            .animation(.easeInOut(duration: 0.25), value: selectedTab)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom) {
                HStack {
                    TabBarButton(icon: "house.fill", label: "Home", isSelected: selectedTab == 0, action: { selectedTab = 0 }, namespace: tabNamespace)
                    TabBarButton(icon: "calendar", label: "Plan", isSelected: selectedTab == 1, action: { selectedTab = 1 }, namespace: tabNamespace)
                    Spacer().frame(width: 56)
                    TabBarButton(icon: "dollarsign.circle.fill", label: "Pay", isSelected: selectedTab == 2, action: { selectedTab = 2 }, namespace: tabNamespace)
                    TabBarButton(icon: "person.3.fill", label: "Team", isSelected: selectedTab == 3, action: { selectedTab = 3 }, namespace: tabNamespace)
                }
                .padding(.horizontal, 8)
                .frame(height: 64)
                .background(
                    Rectangle()
                        .fill(Color(UIColor.systemBackground))
                        .shadow(color: .black.opacity(0.08), radius: 8, y: -2)
                )
            }

            Button(action: { showAddMenu = true }) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [.primaryGreen, Color(red: 0.106, green: 0.263, blue: 0.196)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 56, height: 56)
                        .shadow(color: .black.opacity(0.2), radius: 8, y: 4)

                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                        .rotationEffect(.degrees(showAddMenu ? 45 : 0))
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: showAddMenu)
                }
            }
            .padding(.bottom, 20)
            .confirmationDialog("Add Shift", isPresented: $showAddMenu) {
                Button("Add Single Shift") {
                    editingShiftId = nil
                    showAddShift = true
                }
                Button("Plan Entire Week") {
                    showWeekPlan = true
                }
                Button("Cancel", role: .cancel) {}
            }
        }
        .sheet(isPresented: $showAddShift) {
            NavigationStack {
                AddShiftView(shiftId: editingShiftId)
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showWeekPlan) {
            NavigationStack {
                WeekPlanView()
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showProfile) {
            ProfileView(
                onNavigateToInsights: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showInsights = true
                    }
                },
                onNavigateToJobs: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showJobsView = true
                    }
                }
            )
            .environmentObject(authViewModel)
            .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showInsights) {
            InsightsView()
                .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showJobsView) {
            NavigationStack {
                JobsView()
                    .environmentObject(dashboardViewModel)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Done") { showJobsView = false }
                        }
                    }
            }
        }
    }
}

private struct TabBarButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let action: () -> Void
    var namespace: Namespace.ID? = nil

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                ZStack {
                    if isSelected, let ns = namespace {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.primaryGreen.opacity(0.1))
                            .matchedGeometryEffect(id: "tabIndicator", in: ns)
                    } else {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.clear)
                    }
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .scaleEffect(isSelected ? 1.1 : 1.0)
                }
                .frame(height: 28)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                Text(label)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
            }
            .foregroundColor(isSelected ? .primaryGreen : .secondary)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
        }
        .frame(maxWidth: .infinity)
    }
}
