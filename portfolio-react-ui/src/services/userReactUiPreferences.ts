import { gql, useMutation, useQuery } from "@apollo/client";
import { useEffect, useRef, useState } from "react";

type UserReactPrefEntry = {
  key: string;
  value: string | null;
};

const USER_REACT_UI_PREFS_QUERY = gql`
  query UserReactUIPreferences($clientId: String) {
    userReactUIPreferences(clientId: $clientId) {
      key
      value
    }
  }
`;

const UPDATE_USER_REACT_UI_PREFS_MUTATION = gql`
  mutation UpdateUserReactUIPreferences(
    $clientId: String
    $entries: [KeyValueInputInput]
  ) {
    updateUserReactUIPreferences(clientId: $clientId, entries: $entries) {
      key
      value
    }
  }
`;

type UseUserReactUIPreferenceOptions<T> = {
  clientId: string | null;
  key: string;
  defaultValue: T;
  parse: (raw: string | null) => T;
  serialize: (value: T) => string;
};

export const useUserReactUIPreference = <T>({
  clientId,
  key,
  defaultValue,
  parse,
  serialize,
}: UseUserReactUIPreferenceOptions<T>) => {
  const [value, setValue] = useState<T>(defaultValue);
  const [loaded, setLoaded] = useState(false);
  const [hasPrefValue, setHasPrefValue] = useState(false);
  const lastSerializedRef = useRef<string | null>(null);

  const { data } = useQuery<{ userReactUIPreferences: UserReactPrefEntry[] | null }>(
    USER_REACT_UI_PREFS_QUERY,
    {
      variables: { clientId },
      skip: !clientId,
      fetchPolicy: "cache-and-network",
    }
  );

  const [updatePrefs] = useMutation(UPDATE_USER_REACT_UI_PREFS_MUTATION);

  useEffect(() => {
    setLoaded(false);
    setHasPrefValue(false);
    lastSerializedRef.current = null;
    setValue(defaultValue);
  }, [clientId]);

  useEffect(() => {
    if (!data) return;
    const raw = data.userReactUIPreferences?.find((entry) => entry.key === key)?.value;
    if (raw == null) {
      setHasPrefValue(false);
      setLoaded(true);
      return;
    }
    setHasPrefValue(true);
    setValue(parse(raw));
    lastSerializedRef.current = raw;
    setLoaded(true);
  }, [data, key, parse]);

  useEffect(() => {
    if (loaded && !hasPrefValue) {
      setValue(defaultValue);
    }
  }, [defaultValue, loaded, hasPrefValue]);

  useEffect(() => {
    if (!clientId || !loaded) return;
    const save = async () => {
      const serialized = serialize(value);
      if (serialized === lastSerializedRef.current) {
        return;
      }
      lastSerializedRef.current = serialized;
      await updatePrefs({
        variables: {
          clientId,
          entries: [{ key, value: serialized }],
        },
      });
    };
    void save();
  }, [clientId, key, loaded, serialize, updatePrefs, value]);

  return { value, setValue, loaded };
};
