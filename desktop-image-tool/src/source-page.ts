// This function is serialized into the source page. Keep every dependency inside it.
export async function readSourcePage(
  action: string,
  body: Record<string, unknown>,
  responseLimit: number,
): Promise<Record<string, unknown>> {
  const actions = [
    'DescribeUser',
    'ListProjects',
    'ListScripts',
    'ListCharacters',
    'ListScenes',
    'ListProps',
  ];
  if (
    !actions.includes(action) ||
    window.location.origin !== 'https://console.volcengine.com'
  )
    return { unsupported: true };
  type Row = Record<string, unknown>;
  const row = (value: unknown): Row | undefined =>
    value !== null && typeof value === 'object' && !Array.isArray(value)
      ? (value as Row)
      : undefined;
  const pick = (value: unknown, keys: string[]): Row => {
    const source = row(value);
    if (!source) throw new Error('shape');
    const selected: Row = {};
    for (const key of keys)
      if (source[key] !== undefined) selected[key] = source[key];
    return selected;
  };
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    type Service = Record<string, (input: Row) => Promise<unknown>>;
    type Runtime = { (id: string): Row; m: Record<string, () => unknown> };
    let service: Service | undefined;
    // Navigation can finish before the source application has installed its SDK.
    const deadline = Date.now() + 10000;
    do {
      const chunks = (window as unknown as Row).webpackChunkshort_drama_fe as
        | unknown[]
        | undefined;
      if (Array.isArray(chunks)) {
        let runtime: Runtime | undefined;
        const marker = `antv_read_${Date.now()}_${Math.random()}`;
        const chunk = [
          [marker],
          {},
          (value: Runtime) => {
            runtime = value;
          },
        ];
        chunks.push(chunk);
        if (chunks[chunks.length - 1] === chunk) chunks.pop();
        if (runtime) {
          const loader: Runtime = runtime;
          const moduleId = Object.keys(loader.m).find((id) => {
            const code = loader.m[id].toString();
            return ['DescribeUser', 'ListProjects', 'ListScripts'].every(
              (name) => code.includes(name),
            );
          });
          if (moduleId)
            service = Object.values(loader(moduleId)).find((value) =>
              actions.every((name) => typeof row(value)?.[name] === 'function'),
            ) as Service | undefined;
        }
      }
      if (!service) await new Promise((resolve) => setTimeout(resolve, 200));
    } while (!service && Date.now() < deadline);
    if (!service) return { unavailable: true };
    const value = await Promise.race([
      service[action](body),
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => reject(new Error('timeout')), 30000);
      }),
    ]);
    const root = row(value);
    const error = row(row(root?.ResponseMetadata)?.Error) ?? row(root?.Error);
    if (error) return { status: 200, value: { Error: pick(error, ['Code']) } };
    const result = row(root?.Result);
    if (!result) return { malformed: true };
    const selected: Row = {};
    if (action === 'DescribeUser') {
      const teams = row(result.MemDescription)?.Teams;
      if (!Array.isArray(teams) || teams.length > 10000)
        return { malformed: true };
      selected.MemDescription = {
        Teams: teams.map((team) => pick(team, ['TeamID'])),
      };
    } else {
      if (!Array.isArray(result.Items) || result.Items.length > 100000)
        return { malformed: true };
      const asset = (input: unknown, depth = 0): Row => {
        if (depth > 2) throw new Error('shape');
        const source = row(input);
        const item = pick(input, [
          'CharacterId',
          'SceneId',
          'PropId',
          'ParentCharacterId',
          'ParentSceneId',
          'ParentPropId',
          'ParentId',
          'SourceParentId',
          'Name',
        ]);
        if (source?.Meta !== undefined) item.Meta = pick(source.Meta, ['Name']);
        if (source?.Variants !== undefined) {
          if (!Array.isArray(source.Variants)) throw new Error('shape');
          item.Variants = source.Variants.map((variant) =>
            asset(variant, depth + 1),
          );
        }
        return item;
      };
      selected.Items = result.Items.map((input) => {
        if (action === 'ListScripts')
          return pick(input, ['ScriptId', 'ProjectId', 'TeamId']);
        if (action !== 'ListProjects') return asset(input);
        const item = pick(input, [
          'ProjectId',
          'TeamId',
          'ScriptId',
          'ProjectName',
          'Name',
        ]);
        const source = row(input);
        if (!item.ProjectName && !item.Name && source?.Meta !== undefined)
          item.Meta = pick(source.Meta, ['Name']);
        return item;
      });
      for (const key of ['Total', 'PageIndex', 'PageSize'])
        if (result[key] !== undefined) selected[key] = result[key];
    }
    const output = { status: 200, value: { Result: selected } };
    return JSON.stringify(output).length > responseLimit
      ? { oversized: true }
      : output;
  } catch (failure) {
    const response = row(row(failure)?.response) ?? row(failure);
    const data = row(response?.data) ?? response;
    const error = row(row(data?.ResponseMetadata)?.Error) ?? row(data?.Error);
    return {
      status: typeof response?.status === 'number' ? response.status : 0,
      ...(error ? { value: { Error: pick(error, ['Code']) } } : {}),
      failed: true,
    };
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}
