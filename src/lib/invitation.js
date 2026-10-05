export const ACCEPTED_AT = 'invitation_accepted_at'

export function invitationState(user) {
  if (!user?.invited_at) return 'none'
  return user.user_metadata?.[ACCEPTED_AT] ? 'accepted' : 'pending'
}
