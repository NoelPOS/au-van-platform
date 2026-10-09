import { useQuery } from "@tanstack/react-query";
import { officialAccountUrl } from "../services/liffConfig";
import { isOfficialAccountFriend } from "../services/liffService";

export function useLineFriendship() {
  const addUrl = officialAccountUrl();
  const friendship = useQuery({
    queryKey: ["line", "friendship"],
    queryFn: isOfficialAccountFriend,
    enabled: addUrl !== null,
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
  return {
    addUrl,
    notFriend: addUrl !== null && friendship.data === false,
  };
}
